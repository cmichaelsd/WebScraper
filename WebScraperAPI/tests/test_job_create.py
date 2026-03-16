import pytest
from pydantic import ValidationError

from app.api.schemas.job_create import JobCreate


# ---------------------------------------------------------------------------
# max_depth
# ---------------------------------------------------------------------------

class TestMaxDepth:
    def test_default_is_2(self):
        job = JobCreate(seed_urls=["https://example.com"])
        assert job.max_depth == 2

    def test_zero_accepted(self):
        job = JobCreate(seed_urls=["https://example.com"], max_depth=0)
        assert job.max_depth == 0

    def test_ten_accepted(self):
        job = JobCreate(seed_urls=["https://example.com"], max_depth=10)
        assert job.max_depth == 10

    def test_negative_rejected(self):
        with pytest.raises(ValidationError):
            JobCreate(seed_urls=["https://example.com"], max_depth=-1)

    def test_eleven_rejected(self):
        with pytest.raises(ValidationError):
            JobCreate(seed_urls=["https://example.com"], max_depth=11)


# ---------------------------------------------------------------------------
# seed_urls count
# ---------------------------------------------------------------------------

class TestSeedUrlCount:
    def test_single_url_accepted(self):
        job = JobCreate(seed_urls=["https://example.com"])
        assert len(job.seed_urls) == 1

    def test_exactly_50_accepted(self):
        urls = [f"https://example.com/page/{i}" for i in range(50)]
        job = JobCreate(seed_urls=urls)
        assert len(job.seed_urls) == 50

    def test_51_rejected(self):
        urls = [f"https://example.com/page/{i}" for i in range(51)]
        with pytest.raises(ValidationError, match="Maximum 50 seed URLs allowed"):
            JobCreate(seed_urls=urls)

    def test_empty_list_rejected(self):
        # The for-loop in validate_seed_urls never runs, leaving normalized empty.
        with pytest.raises(ValidationError, match="At least one seed URL required"):
            JobCreate(seed_urls=[])


# ---------------------------------------------------------------------------
# localhost rejection
# ---------------------------------------------------------------------------

class TestLocalhostRejection:
    def test_localhost_rejected(self):
        with pytest.raises(ValidationError, match="Localhost URLs are not allowed"):
            JobCreate(seed_urls=["http://localhost/path"])

    def test_127_0_0_1_rejected(self):
        with pytest.raises(ValidationError, match="Localhost URLs are not allowed"):
            JobCreate(seed_urls=["http://127.0.0.1/path"])

    def test_localhost_mixed_with_valid_still_rejected(self):
        # Even one bad URL in the list should fail the whole request.
        with pytest.raises(ValidationError, match="Localhost URLs are not allowed"):
            JobCreate(seed_urls=["https://example.com", "http://localhost/api"])


# ---------------------------------------------------------------------------
# URL normalization and deduplication
# ---------------------------------------------------------------------------

class TestUrlNormalization:
    def test_fragment_is_stripped(self):
        job = JobCreate(seed_urls=["https://example.com/page#section"])
        assert all("#" not in str(u) for u in job.seed_urls)

    def test_duplicate_urls_deduplicated(self):
        job = JobCreate(seed_urls=[
            "https://example.com/page",
            "https://example.com/page",
        ])
        assert len(job.seed_urls) == 1

    def test_same_url_different_fragments_deduplicated(self):
        # After stripping fragments these are the same URL, so only one survives.
        job = JobCreate(seed_urls=[
            "https://example.com/page#intro",
            "https://example.com/page#conclusion",
        ])
        assert len(job.seed_urls) == 1

    def test_51_identical_urls_still_fails_count_check(self):
        # limit_seed_count runs before deduplication, so 51 raw URLs always fails
        # even if they would collapse to 1 unique URL after normalization.
        urls = ["https://example.com/page"] * 51
        with pytest.raises(ValidationError, match="Maximum 50 seed URLs allowed"):
            JobCreate(seed_urls=urls)


# ---------------------------------------------------------------------------
# URL scheme validation (delegated to Pydantic's HttpUrl)
# ---------------------------------------------------------------------------

class TestUrlSchemes:
    def test_https_accepted(self):
        job = JobCreate(seed_urls=["https://example.com"])
        assert len(job.seed_urls) == 1

    def test_http_accepted(self):
        job = JobCreate(seed_urls=["http://example.com"])
        assert len(job.seed_urls) == 1

    def test_ftp_rejected(self):
        with pytest.raises(ValidationError):
            JobCreate(seed_urls=["ftp://example.com/file.txt"])

    def test_bare_string_rejected(self):
        with pytest.raises(ValidationError):
            JobCreate(seed_urls=["not-a-url"])

    def test_missing_scheme_rejected(self):
        with pytest.raises(ValidationError):
            JobCreate(seed_urls=["example.com"])
