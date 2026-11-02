import os
import tempfile
import unittest
from datetime import date

from listing_harness import ingest

MANUAL = "date,visitors,installs\n2026-11-01,40,12\n2026-11-02,50,10\n"
PLAY = ("Date,Package Name,Country,Store Listing Visitors,Store Listing Acquisitions,Store Listing Conversion Rate\r\n"
        "2026-11-01,com.x,US,30,9,30.00%\r\n2026-11-01,com.x,IN,10,2,20.00%\r\n")


class ParseTests(unittest.TestCase):
    def test_manual(self):
        rows = ingest.parse_rows(MANUAL)
        self.assertEqual(rows[0], {"date": "2026-11-01", "country": "", "source": "", "visitors": 40, "installs": 12})

    def test_play_export_utf16(self):
        buf = b"\xff\xfe" + PLAY.encode("utf-16-le")
        rows = ingest.parse_rows(ingest.decode(buf))
        self.assertEqual([(r["country"], r["visitors"], r["installs"]) for r in rows], [("US", 30, 9), ("IN", 10, 2)])

    def test_utf8_bom_and_commas(self):
        rows = ingest.parse_rows(ingest.decode(b"\xef\xbb\xbf" + b'date,visitors,installs\n2026-11-01,"1,200",300\n'))
        self.assertEqual(rows[0]["visitors"], 1200)

    def test_missing_column_message_lists_headers(self):
        with self.assertRaises(ValueError) as cm:
            ingest.parse_rows("date,foo\n2026-11-01,3\n")
        self.assertIn("headers seen", str(cm.exception))

    def test_installs_above_visitors_rejected(self):
        with self.assertRaises(ValueError):
            ingest.parse_rows("date,visitors,installs\n2026-11-01,5,9\n")

    def test_bad_date_rejected(self):
        with self.assertRaises(ValueError):
            ingest.parse_rows("date,visitors,installs\n11/01/2026,5,1\n")

    def test_blank_cells_are_zero(self):
        self.assertEqual(ingest.parse_rows("date,visitors,installs\n2026-11-01,-,\n")[0]["installs"], 0)


class StoreTests(unittest.TestCase):
    def test_upsert_overwrites_revised_numbers_and_roundtrips(self):
        p = os.path.join(tempfile.mkdtemp(), "m.csv")
        s = ingest.Store(p)
        self.assertEqual(s.upsert(ingest.parse_rows(MANUAL)), 2)
        s.save()
        s2 = ingest.Store(p)
        self.assertEqual(s2.upsert(ingest.parse_rows(MANUAL)), 0)
        revised = ingest.parse_rows("date,visitors,installs\n2026-11-01,41,12\n")
        self.assertEqual(s2.upsert(revised), 1)
        self.assertEqual(len(s2.rows), 2)

    def test_sum_range_and_filters(self):
        rows = ingest.parse_rows(PLAY)
        self.assertEqual(ingest.sum_range(rows, date(2026, 11, 1), date(2026, 11, 1)), (40, 11, 1))
        self.assertEqual(ingest.sum_range(rows, date(2026, 11, 1), date(2026, 11, 1), {"US"}), (30, 9, 1))
        self.assertEqual(ingest.sum_range(rows, date(2026, 11, 2), date(2026, 11, 3))[2], 0)

    def test_double_count_guard(self):
        mixed = ingest.parse_rows(PLAY) + ingest.parse_rows(MANUAL)
        self.assertTrue(ingest.double_count_guard(mixed))
        self.assertFalse(ingest.double_count_guard(ingest.parse_rows(PLAY)))


class GcsNamingTests(unittest.TestCase):
    def test_bucket(self):
        self.assertEqual(ingest.bucket_name("123456789012345"), "pubsite_prod_rev_123456789012345")
        self.assertEqual(ingest.bucket_name("gs://pubsite_prod_rev_123456789012345/"), "pubsite_prod_rev_123456789012345")
        with self.assertRaises(ingest.NotConfigured):
            ingest.bucket_name("not-a-number")

    def test_object_name(self):
        self.assertEqual(ingest.gcs_object_name("com.x", "202611", "country"),
                         "stats/store_performance/store_performance_com.x_202611_country.csv")
        with self.assertRaises(ValueError):
            ingest.gcs_object_name("com.x", "202611", "../etc")

    def test_scope_is_read_only(self):
        self.assertTrue(ingest.GCS_SCOPE.endswith("devstorage.read_only"))


if __name__ == "__main__":
    unittest.main()
