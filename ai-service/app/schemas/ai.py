from pydantic import BaseModel, Field


class TextRequest(BaseModel):
    text: str = Field(..., min_length=20, description="Input text for AI analysis")


class SummaryRequest(TextRequest):
    max_sentences: int = Field(default=3, ge=1, le=8)


class RewriteRequest(TextRequest):
    mode: str = Field(default="professional", pattern="^(professional|formal|simplify|shorten|key-points|readability)$")


class QuestionRequest(TextRequest):
    question: str = Field(..., min_length=3, max_length=400)


class SummaryResponse(BaseModel):
    summary: str
    title_suggestion: str
    key_insights: list[str]
    bullet_highlights: list[str]
    readability_note: str


class TagsResponse(BaseModel):
    tags: list[str]


class RewriteResponse(BaseModel):
    rewritten_text: str
    mode: str


class QuestionResponse(BaseModel):
    question: str
    answer: str
    evidence_snippet: str | None = None
    answered: bool


class HealthResponse(BaseModel):
    status: str
    service: str
    message: str
