from fastapi import APIRouter

from app.schemas.ai import (
    HealthResponse,
    QuestionRequest,
    QuestionResponse,
    RewriteRequest,
    RewriteResponse,
    SummaryRequest,
    SummaryResponse,
    TagsResponse,
    TextRequest,
)
from app.services.ai_service import ai_service

router = APIRouter()


@router.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(
        status="UP",
        service="filemind-ai-service",
        message="AI text processing service is healthy.",
    )


@router.post("/summarize", response_model=SummaryResponse)
def summarize(payload: SummaryRequest) -> SummaryResponse:
    return ai_service.summarize(payload.text, payload.max_sentences)


@router.post("/tags", response_model=TagsResponse)
def tags(payload: TextRequest) -> TagsResponse:
    return ai_service.generate_tags(payload.text)


@router.post("/rewrite", response_model=RewriteResponse)
def rewrite(payload: RewriteRequest) -> RewriteResponse:
    return ai_service.rewrite(payload.text, payload.mode)


@router.post("/ask", response_model=QuestionResponse)
def ask(payload: QuestionRequest) -> QuestionResponse:
    return ai_service.answer_question(payload.text, payload.question)
