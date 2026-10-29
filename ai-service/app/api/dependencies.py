from typing import Annotated

from fastapi import Depends, HTTPException, Request

from app.core.runtime import Runtime


def get_runtime(request: Request) -> Runtime:
    runtime = getattr(request.app.state, "runtime", None)
    if runtime is None or not runtime.ready:
        raise HTTPException(503, "AI Service chưa sẵn sàng", headers={"Retry-After": "5"})
    return runtime


RuntimeDependency = Annotated[Runtime, Depends(get_runtime)]
