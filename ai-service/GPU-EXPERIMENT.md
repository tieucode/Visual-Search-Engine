# GPU experiment

GPU execution is selected with `DEVICE=cuda`. Startup checks CUDA availability before loading models. The normal Dockerfile builds the CPU image. `Dockerfile.gpu` layers CUDA-enabled PyTorch on that image and copies the current application source.

The experiment uses GTX 1650 4 GiB, Windows NVIDIA driver 566.07, Docker Desktop WSL2, PyTorch 2.7.1 + CUDA 12.6 and torchvision 0.22.1. SigLIP uses float32 and EasyOCR uses CUDA. Model, OCR languages, input dimensions and microbatch size match the CPU experiment.

Build and start the GPU experiment on port 8002 from the repository root.

```powershell
docker build -f ai-service/Dockerfile.gpu -t visual-search-ai-service:gpu-test ai-service
docker run --detach --name visual-search-ai-gpu-test --gpus all --restart no -p 127.0.0.1:8002:8000 --mount type=volume,source=visual-search-engine_ai_models_cache,target=/root/.cache -e DEVICE=cuda -e SIGLIP_MODEL=google/siglip2-base-patch16-224 -e OCR_LANGUAGES=en,vi -e TORCH_NUM_THREADS=4 -e EMBEDDING_MICROBATCH_SIZE=2 visual-search-ai-service:gpu-test
Invoke-RestMethod http://localhost:8002/health
docker logs --tail 30 visual-search-ai-gpu-test
```

Wait for model warmup and a successful health response. Check GPU access inside the container.

```powershell
docker exec visual-search-ai-gpu-test python -c "import torch; print(torch.__version__, torch.version.cuda, torch.cuda.is_available(), torch.cuda.get_device_name(0))"
```

The benchmark requires local Python with `requests` and `Pillow`. It prepares the first 100 CSV images on `images.unsplash.com` as JPEGs bounded to 1024 px, stores them under ignored `ai-service/target/gpu-benchmark`, and reuses the same files for every endpoint. Preparation fails explicitly if an image cannot be fetched. Each endpoint receives a warmup batch, followed by groups of three images. Multiple endpoints receive separate lanes concurrently, at most one request per endpoint. The benchmark calls only the AI indexing HTTP endpoint and never writes to PostgreSQL or Qdrant.

```powershell
python ai-service/benchmark_indexing.py --case gpu-one=http://127.0.0.1:8002 --gpu-metrics
python ai-service/benchmark_indexing.py --case cpu-one=http://127.0.0.1:8003
python ai-service/benchmark_indexing.py --case cpu-two=http://127.0.0.1:8003,http://127.0.0.1:8004
```

Results, errors, OCR region counts and input SHA-256 hashes are written under `ai-service/target/gpu-benchmark`. GPU metrics sample `nvidia-smi` every 0.5 seconds, so sampled peak memory can miss brief spikes. Throughput includes HTTP transport and model processing after warmup, excludes image download and startup, and is not full importer throughput. GPU and existing CPU images use different PyTorch builds, so comparisons describe those deployments. CUDA runs can produce small floating-point and OCR differences.

Stop the experiment when finished.

```powershell
docker stop visual-search-ai-gpu-test
```

PyTorch version pairing comes from the [official previous versions page](https://docs.pytorch.org/get-started/previous-versions/). CUDA 12.6 driver requirements are documented in the [NVIDIA release notes](https://docs.nvidia.com/cuda/archive/12.6.0/cuda-toolkit-release-notes/index.html).
