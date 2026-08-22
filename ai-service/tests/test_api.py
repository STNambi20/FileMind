import unittest

from fastapi.testclient import TestClient

from app.main import app


class FileMindAiApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(app)

    def test_health_endpoint(self):
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["status"], "UP")

    def test_summary_tags_and_rewrite_endpoints(self):
        text = (
            "FileMind stores document versions and tracks storage savings through deduplication. "
            "The platform extracts text, generates AI summaries, and helps teams collaborate "
            "through governed access controls."
        )

        summary_response = self.client.post("/summarize", json={"text": text, "max_sentences": 2})
        tags_response = self.client.post("/tags", json={"text": text})
        rewrite_response = self.client.post("/rewrite", json={"text": text, "mode": "professional"})
        formal_rewrite_response = self.client.post("/rewrite", json={"text": text, "mode": "formal"})
        readability_response = self.client.post("/rewrite", json={"text": text, "mode": "readability"})
        key_points_response = self.client.post("/rewrite", json={"text": text, "mode": "key-points"})
        question_response = self.client.post(
            "/ask",
            json={"text": text, "question": "How does FileMind track storage savings?"},
        )

        self.assertEqual(summary_response.status_code, 200)
        self.assertTrue(summary_response.json()["summary"])
        self.assertTrue(summary_response.json()["title_suggestion"])
        self.assertGreater(len(summary_response.json()["key_insights"]), 0)
        self.assertGreater(len(summary_response.json()["bullet_highlights"]), 0)
        self.assertTrue(summary_response.json()["readability_note"])

        self.assertEqual(tags_response.status_code, 200)
        self.assertGreater(len(tags_response.json()["tags"]), 0)

        self.assertEqual(rewrite_response.status_code, 200)
        self.assertEqual(rewrite_response.json()["mode"], "professional")
        self.assertTrue(rewrite_response.json()["rewritten_text"])

        self.assertEqual(formal_rewrite_response.status_code, 200)
        self.assertEqual(formal_rewrite_response.json()["mode"], "formal")
        self.assertTrue(formal_rewrite_response.json()["rewritten_text"])
        self.assertNotEqual(
            rewrite_response.json()["rewritten_text"],
            formal_rewrite_response.json()["rewritten_text"],
        )
        self.assertIn("Moreover", formal_rewrite_response.json()["rewritten_text"])

        self.assertEqual(readability_response.status_code, 200)
        self.assertEqual(readability_response.json()["mode"], "readability")
        self.assertTrue(readability_response.json()["rewritten_text"])
        self.assertNotEqual(
            rewrite_response.json()["rewritten_text"],
            readability_response.json()["rewritten_text"],
        )

        self.assertEqual(key_points_response.status_code, 200)
        self.assertEqual(key_points_response.json()["mode"], "key-points")
        self.assertIn("- ", key_points_response.json()["rewritten_text"])

        self.assertEqual(question_response.status_code, 200)
        self.assertTrue(question_response.json()["answered"])
        self.assertTrue(question_response.json()["answer"])
        self.assertTrue(question_response.json()["evidence_snippet"])


if __name__ == "__main__":
    unittest.main()
