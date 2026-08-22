import math
import re
import unicodedata
from collections import Counter
from typing import Protocol

from app.core.config import settings
from app.schemas.ai import QuestionResponse, RewriteResponse, SummaryResponse, TagsResponse

STOP_WORDS = {
    "a", "an", "and", "are", "as", "at", "be", "by", "for", "from", "has", "in",
    "is", "it", "of", "on", "or", "that", "the", "this", "to", "was", "were",
    "with", "will", "can", "into", "your", "their", "about", "than", "then",
    "also", "such", "these", "those", "have", "having", "been", "being", "over",
    "under", "after", "before", "each", "through", "while", "where", "when",
}


class AiProvider(Protocol):
    def summarize(self, text: str, max_sentences: int) -> SummaryResponse:
        ...

    def generate_tags(self, text: str) -> TagsResponse:
        ...

    def rewrite(self, text: str, mode: str) -> RewriteResponse:
        ...

    def answer_question(self, text: str, question: str) -> QuestionResponse:
        ...


class HeuristicAiProvider:
    def summarize(self, text: str, max_sentences: int) -> SummaryResponse:
        prepared_text = self._prepare_text(text)
        summary = self._ranked_summary(prepared_text, max_sentences)
        summary_sentences = self._sentences(summary)
        return SummaryResponse(
            summary=self._sanitize_output(summary),
            title_suggestion=self._sanitize_output(self._suggest_title(prepared_text)),
            key_insights=[self._sanitize_output(item) for item in self._key_insights(summary_sentences)],
            bullet_highlights=[self._sanitize_output(item) for item in self._bullet_highlights(summary_sentences)],
            readability_note=self._sanitize_output(self._readability_note(prepared_text)),
        )

    def generate_tags(self, text: str) -> TagsResponse:
        prepared_text = self._prepare_text(text)
        words = [word for word in self._words(prepared_text) if len(word) > 3 and word not in STOP_WORDS]
        unigram_scores = Counter(words)
        phrase_scores = Counter()

        for sentence in self._sentences(prepared_text):
            sentence_words = [word for word in self._words(sentence) if len(word) > 3 and word not in STOP_WORDS]
            for left, right in zip(sentence_words, sentence_words[1:]):
                if left == right:
                    continue
                phrase_scores[f"{left} {right}"] += 1

        candidates = []
        for phrase, count in phrase_scores.items():
            left, right = phrase.split(" ", 1)
            score = count * 3 + unigram_scores[left] + unigram_scores[right]
            if score >= 4:
                candidates.append((score, phrase))

        for word, count in unigram_scores.items():
            candidates.append((count, word))

        tags = []
        seen_keys = set()
        for _, candidate in sorted(candidates, reverse=True):
            sanitized_candidate = self._sanitize_tag(candidate)
            dedupe_key = self._tag_dedupe_key(sanitized_candidate)
            if not sanitized_candidate or dedupe_key in seen_keys:
                continue
            seen_keys.add(dedupe_key)
            tags.append(sanitized_candidate)
            if len(tags) == 6:
                break

        return TagsResponse(tags=tags)

    def rewrite(self, text: str, mode: str) -> RewriteResponse:
        prepared_text = self._prepare_text(text)
        sentences = self._sentences(prepared_text)
        if mode == "shorten":
            rewritten = self._ranked_summary(prepared_text, max(1, min(2, len(sentences))))
        elif mode == "key-points":
            rewritten = self._key_points_rewrite(prepared_text)
        elif mode == "formal":
            rewritten = self._formal_rewrite(prepared_text)
        elif mode == "simplify":
            rewritten = self._simplify_text(prepared_text)
        elif mode == "readability":
            rewritten = self._readability_rewrite(prepared_text)
        else:
            rewritten = self._professional_rewrite(prepared_text)

        return RewriteResponse(rewritten_text=self._sanitize_output(rewritten), mode=mode)

    def answer_question(self, text: str, question: str) -> QuestionResponse:
        prepared_text = self._prepare_text(text)
        prepared_question = self._prepare_text(question)
        question_tokens = [token for token in self._words(prepared_question) if token not in STOP_WORDS and len(token) > 2]
        sentences = self._sentences(prepared_text)

        if not sentences or not question_tokens:
            return QuestionResponse(
                question=question.strip(),
                answer="I could not find enough evidence in the document to answer that clearly.",
                evidence_snippet=None,
                answered=False,
            )

        ranked: list[tuple[float, int, str]] = []
        for index, sentence in enumerate(sentences):
            sentence_words = self._words(sentence)
            if not sentence_words:
                continue
            overlap = sum(1 for token in question_tokens if token in sentence_words)
            if overlap == 0:
                continue
            coverage = overlap / max(1, len(set(question_tokens)))
            contains_phrase = 1.0 if prepared_question.lower() in sentence.lower() else 0.0
            ranked.append((coverage + contains_phrase, index, sentence))

        if not ranked:
            return QuestionResponse(
                question=question.strip(),
                answer="I could not find enough evidence in the document to answer that clearly.",
                evidence_snippet=None,
                answered=False,
            )

        _, index, sentence = sorted(ranked, reverse=True)[0]
        evidence = sentence
        if len(evidence) < 120 and index + 1 < len(sentences):
            evidence = f"{sentence} {sentences[index + 1]}"

        cleaned_evidence = self._sanitize_output(evidence)
        answer = self._sanitize_output(self._cleanup_sentence(sentence))
        return QuestionResponse(
            question=question.strip(),
            answer=answer,
            evidence_snippet=cleaned_evidence,
            answered=True,
        )

    def _ranked_summary(self, text: str, max_sentences: int) -> str:
        sentences = self._sentences(text)
        if len(sentences) <= max_sentences:
            return " ".join(sentences)

        word_scores = self._keyword_scores(text)
        ranked = []
        total_sentences = max(1, len(sentences))
        for index, sentence in enumerate(sentences):
            words = self._words(sentence)
            if not words:
                continue
            keyword_score = sum(word_scores.get(word, 0) for word in words) / math.sqrt(len(words))
            position_bonus = 1.15 if index == 0 else 1.0 + max(0.0, (total_sentences - index) / total_sentences) * 0.05
            length_penalty = 0.9 if len(words) > 38 else 1.0
            ranked.append((keyword_score * position_bonus * length_penalty, index, sentence))

        selected = []
        seen_signatures = set()
        for _, index, sentence in sorted(ranked, reverse=True):
            signature = " ".join(self._words(sentence)[:8])
            if signature in seen_signatures:
                continue
            seen_signatures.add(signature)
            selected.append((index, sentence))
            if len(selected) == max_sentences:
                break

        return " ".join(sentence for _, sentence in sorted(selected, key=lambda item: item[0]))

    def _professional_rewrite(self, text: str) -> str:
        replacements = {
            "make sure": "ensure",
            "helps": "supports",
            "help": "support",
            "shows": "highlights",
            "show": "highlight",
            "track": "monitor",
            "tracks": "monitors",
        }
        sentences = [self._rewrite_sentence(sentence, replacements) for sentence in self._sentences(text)]
        return " ".join(sentence for sentence in sentences if sentence)

    def _formal_rewrite(self, text: str) -> str:
        replacements = {
            "help": "facilitate",
            "helps": "facilitates",
            "show": "demonstrate",
            "shows": "demonstrates",
            "need": "require",
            "needs": "requires",
            "use": "utilize",
            "uses": "utilizes",
            "improve": "enhance",
            "improves": "enhances",
            "track": "monitor",
            "tracks": "monitors",
            "manage": "administer",
            "manages": "administers",
        }
        transitions = ["Moreover, ", "Furthermore, ", "In addition, ", "Accordingly, ", "Finally, "]
        rewritten_sentences = []

        for index, sentence in enumerate(self._sentences(text)):
            rewritten = self._rewrite_sentence(sentence, replacements)
            if not rewritten:
                continue
            if index == 0:
                rewritten_sentences.append(rewritten)
            else:
                transition = transitions[(index - 1) % len(transitions)]
                rewritten_sentences.append(f"{transition}{self._lowercase_first(rewritten)}")

        return " ".join(rewritten_sentences)

    def _simplify_text(self, text: str) -> str:
        replacements = {
            "utilize": "use",
            "approximately": "about",
            "facilitate": "help",
            "commence": "start",
            "terminate": "end",
            "demonstrate": "show",
        }
        simplified = text
        for original, replacement in replacements.items():
            simplified = re.sub(fr"\b{original}\b", replacement, simplified, flags=re.IGNORECASE)
        simplified = re.sub(r"\s+", " ", simplified).strip()
        return " ".join(self._cleanup_sentence(sentence) for sentence in self._sentences(simplified))

    def _readability_rewrite(self, text: str) -> str:
        simplified_summary = self._ranked_summary(text, min(3, max(2, len(self._sentences(text)))))
        clauses = []
        for sentence in self._sentences(simplified_summary):
            for clause in re.split(r",\s+|\s+and\s+|\s+while\s+", sentence):
                cleaned = self._simplify_text(clause)
                cleaned = re.sub(r"\bhowever\b", "but", cleaned, flags=re.IGNORECASE)
                cleaned = re.sub(r"\btherefore\b", "so", cleaned, flags=re.IGNORECASE)
                cleaned = self._cleanup_sentence(cleaned)
                if cleaned and cleaned not in clauses:
                    clauses.append(cleaned)

        concise_clauses = clauses[:4] if clauses else [self._cleanup_sentence(self._simplify_text(text))]
        return " ".join(concise_clauses)

    def _key_points_rewrite(self, text: str) -> str:
        summary = self._ranked_summary(text, min(4, max(2, len(self._sentences(text)))))
        points = []
        for sentence in self._sentences(summary):
            cleaned = self._cleanup_sentence(sentence).rstrip(".")
            if cleaned:
                points.append(f"- {cleaned}")
        return "\n".join(points[:4])

    def _suggest_title(self, text: str) -> str:
        words = [word for word in self._words(text) if len(word) > 3 and word not in STOP_WORDS]
        common = [word.title() for word, _ in Counter(words).most_common(3)]
        if common:
            return " ".join(common)
        return self._cleanup_sentence(self._sentences(text)[0]).rstrip(".")

    def _key_insights(self, sentences: list[str]) -> list[str]:
        return [self._cleanup_sentence(sentence) for sentence in sentences[:3]]

    def _bullet_highlights(self, sentences: list[str]) -> list[str]:
        highlights = []
        for sentence in sentences[:4]:
            cleaned = self._cleanup_sentence(sentence).rstrip(".")
            if cleaned and cleaned not in highlights:
                highlights.append(cleaned)
        return highlights

    def _readability_note(self, text: str) -> str:
        sentences = self._sentences(text)
        word_count = len(self._words(text))
        average_sentence_length = word_count / max(1, len(sentences))
        if average_sentence_length > 24:
            return "Dense wording detected. Shorter sentences would improve readability."
        if average_sentence_length > 18:
            return "Moderate reading effort. A lighter rewrite would improve scanning."
        return "Readable structure with clear sentence flow."

    def _prepare_text(self, text: str) -> str:
        prepared = unicodedata.normalize("NFKC", text).replace("\u00ad", "")
        prepared = re.sub(r"[\u200b-\u200f\u2028\u2029]", "", prepared)
        prepared = re.sub(r"[^\S\r\n]+", " ", prepared)
        prepared = re.sub(r"[•▪◦]", "-", prepared)
        prepared = re.sub(r"-\s*\n", "", prepared)
        prepared = re.sub(r"\s*\n\s*", " ", prepared)
        prepared = re.sub(r"\s+", " ", prepared)
        prepared = prepared.replace("ï¿½", "").replace("�", "")
        return prepared.strip()

    def _rewrite_sentence(self, sentence: str, replacements: dict[str, str]) -> str:
        rewritten = self._cleanup_sentence(sentence)
        for original, replacement in sorted(replacements.items(), key=lambda item: len(item[0]), reverse=True):
            rewritten = re.sub(fr"\b{re.escape(original)}\b", replacement, rewritten, flags=re.IGNORECASE)
        return rewritten

    def _cleanup_sentence(self, sentence: str) -> str:
        cleaned = sentence.strip(" -")
        cleaned = cleaned.replace(" don't ", " do not ")
        cleaned = cleaned.replace(" can't ", " cannot ")
        cleaned = cleaned.replace(" won't ", " will not ")
        cleaned = re.sub(r"\s+", " ", cleaned)
        if cleaned and cleaned[-1] not in ".!?":
            cleaned += "."
        return f"{cleaned[0].upper()}{cleaned[1:]}" if cleaned else cleaned

    def _sanitize_output(self, text: str) -> str:
        sanitized = unicodedata.normalize("NFKC", text)
        sanitized = re.sub(r"[^\S\r\n]+", " ", sanitized)
        sanitized = re.sub(r"\s+([,.;:!?])", r"\1", sanitized)
        sanitized = re.sub(r"([,.;:!?]){2,}", r"\1", sanitized)
        sanitized = sanitized.replace("ï¿½", "").replace("�", "")
        sanitized = sanitized.strip()
        return sanitized

    def _sanitize_tag(self, value: str) -> str:
        return self._sanitize_output(value).strip("- ").lower()

    def _tag_dedupe_key(self, value: str) -> str:
        simplified = value.replace("-", " ").strip()
        simplified = re.sub(r"\s+", " ", simplified)
        if simplified.endswith("s") and len(simplified) > 4:
            simplified = simplified[:-1]
        return simplified

    def _keyword_scores(self, text: str) -> Counter:
        words = [word for word in self._words(text) if word not in STOP_WORDS and len(word) > 2]
        return Counter(words)

    def _words(self, text: str) -> list[str]:
        return re.findall(r"[a-zA-Z']+", text.lower())

    def _sentences(self, text: str) -> list[str]:
        sentences = [segment.strip() for segment in re.split(r"(?<=[.!?])\s+|\n+", text.strip()) if segment.strip()]
        return sentences or [text.strip()]

    def _lowercase_first(self, text: str) -> str:
        return text[:1].lower() + text[1:] if text else text


class ProviderBackedAiService:
    def __init__(self, provider: AiProvider, provider_name: str) -> None:
        self._provider = provider
        self.provider_name = provider_name

    def summarize(self, text: str, max_sentences: int) -> SummaryResponse:
        return self._provider.summarize(text, max_sentences)

    def generate_tags(self, text: str) -> TagsResponse:
        return self._provider.generate_tags(text)

    def rewrite(self, text: str, mode: str) -> RewriteResponse:
        return self._provider.rewrite(text, mode)

    def answer_question(self, text: str, question: str) -> QuestionResponse:
        return self._provider.answer_question(text, question)


def build_ai_service() -> ProviderBackedAiService:
    provider_name = settings.provider.strip().lower()
    if provider_name == "heuristic":
        return ProviderBackedAiService(HeuristicAiProvider(), provider_name)
    raise RuntimeError(f"Unsupported AI provider configured: {provider_name}")


ai_service = build_ai_service()
