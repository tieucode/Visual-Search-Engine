import logging
import os
from pathlib import Path

from app.core.config import Settings

logger = logging.getLogger(__name__)


def available_cpus() -> int:
    count = os.cpu_count() or 1
    try:
        count = min(count, len(os.sched_getaffinity(0)))
    except (AttributeError, OSError):
        pass
    try:
        quota, period = Path("/sys/fs/cgroup/cpu.max").read_text().split()
        if quota != "max":
            count = min(count, max(1, int(quota) // int(period)))
    except (OSError, ValueError, ZeroDivisionError):
        try:
            root = Path("/sys/fs/cgroup/cpu")
            quota = int((root / "cpu.cfs_quota_us").read_text())
            period = int((root / "cpu.cfs_period_us").read_text())
            if quota > 0:
                count = min(count, max(1, quota // period))
        except (OSError, ValueError, ZeroDivisionError):
            pass
    return max(1, count)


def configure_cpu(settings: Settings) -> None:
    count = available_cpus()
    threads = settings.torch_num_threads or max(1, min(4, count // 2))
    os.environ.setdefault("OMP_NUM_THREADS", str(threads))
    os.environ.setdefault("MKL_NUM_THREADS", str(threads))

    import cv2
    import torch

    torch.set_num_threads(threads)
    if torch.get_num_interop_threads() != settings.torch_num_interop_threads:
        torch.set_num_interop_threads(settings.torch_num_interop_threads)
    cv2.setNumThreads(settings.opencv_num_threads)
    logger.info(
        "CPU configuration available=%s torch_threads=%s interop_threads=%s opencv_threads=%s",
        count, threads, settings.torch_num_interop_threads, settings.opencv_num_threads,
    )
