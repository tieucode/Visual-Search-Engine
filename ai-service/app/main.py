from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI(
    title="Visual Search AI Service",
    description="AI Service for Image & Text Embedding (SigLIP) and OCR (EasyOCR)",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health_check():
    return {"status": "healthy", "service": "ai-service"}


@app.get("/")
def root():
    return {"message": "Visual Search AI Service is running"}
