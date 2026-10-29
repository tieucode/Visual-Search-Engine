import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.dependencies import RuntimeDependency
from app.api.embedding import router as embedding_router
from app.api.indexing import router as indexing_router
from app.api.ocr import router as ocr_router
from app.api.process import router as process_router
from app.core.config import settings
from app.core.runtime import Runtime


@asynccontextmanager
async def lifespan(app: FastAPI):
    logging.basicConfig(level=logging.DEBUG if settings.debug else logging.INFO)
    runtime = Runtime(settings)
    app.state.runtime = runtime
    try:
        await asyncio.to_thread(runtime.start)
        yield
    finally:
        runtime.close()


app = FastAPI(
    title=settings.app_name,
    description="AI Service for Image & Text Embedding (SigLIP) and OCR (EasyOCR)",
    version=settings.app_version,
    lifespan=lifespan,
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
for router in (indexing_router, process_router, embedding_router, ocr_router):
    app.include_router(router)


@app.get("/health")
async def health_check(runtime: RuntimeDependency):
    return {"status": "healthy", "service": "ai-service"}


@app.get("/")
async def root():
    return {"message": "Visual Search AI Service is running"}
