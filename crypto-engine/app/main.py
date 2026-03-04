from fastapi import FastAPI

from app.routers import dsa, kem

app = FastAPI(title="Crypto Engine", version="1.0.0")

app.include_router(kem.router, prefix="/kem", tags=["KEM"])
app.include_router(dsa.router, prefix="/dsa", tags=["DSA"])


@app.get("/health")
def health():
    return {"status": "ok"}
