import json
import logging
import os
import time
from typing import Optional

from infrastructure.persistence.database import get_connection
from infrastructure.sync.sync_outbox_repo import SyncOutboxRepository
from infrastructure.sync.sync_writer import upsert_to_supabase

logger = logging.getLogger(__name__)


class RetryWorker:
    def __init__(self, db=None, batch_size: int = 50,
                 interval_seconds: Optional[int] = None):
        self.db = db if db is not None else get_connection()
        self.batch_size = batch_size
        self.interval_seconds = interval_seconds or int(
            os.environ.get("SYNC_RETRY_INTERVAL_SECONDS", "30")
        )
        self.outbox_repo = SyncOutboxRepository(self.db)

    def tick(self) -> int:
        """Process one batch. Returns count attempted (success or fail)."""
        rows = self.outbox_repo.claim_due(limit=self.batch_size)
        processed = 0
        for row in rows:
            processed += 1
            try:
                payload = json.loads(row["payload"]) if row["payload"] else None
                upsert_to_supabase(row["table_name"], row["op"], row["row_id"], payload)
                self.outbox_repo.mark_done(row["id"])
            except Exception as e:
                logger.warning("retry_worker tick failed row_id=%s err=%s", row["id"], e)
                self.outbox_repo.mark_failed(row["id"], str(e))
        return processed

    def run_forever(self) -> None:
        """Entry point for `python -m infrastructure.sync.retry_worker`."""
        logger.info("retry_worker started, interval=%ss", self.interval_seconds)
        try:
            while True:
                try:
                    self.tick()
                except Exception as e:
                    logger.error("retry_worker tick error: %s", e)
                time.sleep(self.interval_seconds)
        except KeyboardInterrupt:
            logger.info("retry_worker stopped")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    RetryWorker().run_forever()