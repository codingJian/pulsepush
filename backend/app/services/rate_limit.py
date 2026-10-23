import threading
import time
from collections import defaultdict, deque


class SlidingWindowRateLimiter:
    """Pure in-memory sliding window, per key. No Redis needed (fits 961MB VPS)."""

    def __init__(self, limit: int = 60, window_seconds: int = 60):
        self.limit = limit
        self.window = window_seconds
        self._hits: dict[str, deque] = defaultdict(deque)
        self._lock = threading.Lock()

    def allow(self, key: str) -> bool:
        now = time.monotonic()
        cutoff = now - self.window
        with self._lock:
            q = self._hits[key]
            while q and q[0] < cutoff:
                q.popleft()
            if len(q) >= self.limit:
                return False
            q.append(now)
            return True


# Global limiter: 60/min per channel_token (key includes token)
rate_limiter = SlidingWindowRateLimiter(limit=60, window_seconds=60)
