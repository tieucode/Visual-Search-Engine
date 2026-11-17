import asyncio
import logging
import time
from collections import deque
from concurrent.futures import ThreadPoolExecutor
from contextlib import asynccontextmanager
from dataclasses import dataclass
from functools import partial
from typing import Callable, Literal, TypeVar

from app.core.errors import ServiceError

Priority = Literal["search", "background"]
T = TypeVar("T")
logger = logging.getLogger(__name__)


def unavailable(code: str = "OVERLOADED") -> ServiceError:
    message = "Hết thời gian chờ AI Service" if code == "QUEUE_TIMEOUT" else "AI Service đang quá tải hoặc dừng hoạt động"
    return ServiceError(message, code=code, status_code=503, retryable=True)


async def settle(future: asyncio.Future) -> None:
    """Drain running work even if the caller receives repeated cancellations."""
    while not future.done():
        try:
            await asyncio.shield(future)
        except asyncio.CancelledError:
            continue
        except Exception:
            break
    if not future.cancelled():
        future.exception()


@dataclass(eq=False, slots=True)
class Ticket:
    priority: Priority
    future: asyncio.Future
    granted: bool = False


class PriorityGate:
    """Event-loop-owned permits with bounded, weighted queues."""

    def __init__(
        self, limit: int, waiting: int, timeout: float, *,
        reserved_active: int = 0, reserved_waiting: int = 0,
    ) -> None:
        self.limit = limit
        self.waiting = waiting
        self.timeout = timeout
        self.background_limit = max(1, limit - reserved_active)
        self.background_waiting = max(0, waiting - reserved_waiting)
        self.active = self.active_background = self.search_streak = 0
        self.queues: dict[Priority, deque[Ticket]] = {"search": deque(), "background": deque()}
        self.closed = False

    def _available(self, priority: Priority) -> bool:
        return self.active < self.limit and (
            priority == "search" or self.active_background < self.background_limit
        )

    def _grant(self, ticket: Ticket) -> None:
        ticket.granted = True
        self.active += 1
        self.active_background += ticket.priority == "background"
        ticket.future.set_result(None)

    def _dispatch(self) -> None:
        while not self.closed and self.active < self.limit:
            search = bool(self.queues["search"])
            background = bool(self.queues["background"]) and self._available("background")
            if not search and not background:
                return
            if search and (not background or self.search_streak < 4):
                priority = "search"
                self.search_streak = min(4, self.search_streak + 1)
            else:
                priority = "background"
                self.search_streak = 0
            self._grant(self.queues[priority].popleft())

    def _release(self, ticket: Ticket) -> None:
        self.active -= 1
        self.active_background -= ticket.priority == "background"
        self._dispatch()

    @asynccontextmanager
    async def slot(self, priority: Priority):
        if self.closed:
            raise unavailable("SHUTTING_DOWN")
        ticket = Ticket(priority, asyncio.get_running_loop().create_future())
        if self._available(priority) and not self.queues[priority]:
            self._grant(ticket)
        else:
            depth = sum(len(queue) for queue in self.queues.values())
            if depth >= self.waiting or (
                priority == "background" and len(self.queues[priority]) >= self.background_waiting
            ):
                raise unavailable()
            self.queues[priority].append(ticket)
            self._dispatch()
        try:
            await asyncio.wait_for(asyncio.shield(ticket.future), self.timeout)
        except BaseException as exc:
            if ticket.granted:
                self._release(ticket)
            else:
                try:
                    self.queues[priority].remove(ticket)
                except ValueError:
                    pass
                if ticket.future.done() and not ticket.future.cancelled():
                    ticket.future.exception()
                ticket.future.cancel()
            if isinstance(exc, TimeoutError):
                raise unavailable("QUEUE_TIMEOUT") from exc
            raise
        try:
            yield
        finally:
            self._release(ticket)

    def close(self) -> None:
        self.closed = True
        for queue in self.queues.values():
            while queue:
                queue.popleft().future.set_exception(unavailable("SHUTTING_DOWN"))


class ExecutionStage:
    def __init__(self, name: str, waiting: int, timeout: float) -> None:
        self.name = name
        self.gate = PriorityGate(1, waiting, timeout)
        self.executor = ThreadPoolExecutor(max_workers=1, thread_name_prefix=f"ai-{name}")

    async def run(
        self, priority: Priority, operation: Callable[..., T], *args,
        discard: Callable[[T], None] | None = None, **kwargs,
    ) -> T:
        queued = time.perf_counter()
        async with self.gate.slot(priority):
            started = time.perf_counter()
            future = asyncio.get_running_loop().run_in_executor(
                self.executor, partial(operation, *args, **kwargs),
            )
            try:
                return await asyncio.shield(future)
            except asyncio.CancelledError:
                await settle(future)
                if discard and not future.cancelled() and future.exception() is None:
                    discard(future.result())
                raise
            finally:
                logger.info(
                    "AI stage=%s priority=%s queue_ms=%.2f execution_ms=%.2f",
                    self.name, priority, (started - queued) * 1000,
                    (time.perf_counter() - started) * 1000,
                )

    def stop(self) -> None:
        self.gate.close()

    def close(self) -> None:
        # Runtime drains all operations before closing executors.
        self.executor.shutdown(wait=True, cancel_futures=True)
