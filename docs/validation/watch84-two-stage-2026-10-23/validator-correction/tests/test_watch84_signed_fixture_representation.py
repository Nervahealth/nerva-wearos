#!/usr/bin/env python3
"""Focused negative tests for the Watch84 signed-DEX representation correction.

These tests use only preserved exact method-block fixtures extracted read-only from
one signed APK.  They never open the APK, invoke Android tools, build, sign,
repack, install, launch, or perform admission.
"""
from __future__ import annotations

import hashlib
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
import admit_watch84_candidate_corrected as WATCH  # noqa: E402


FIXTURES = ROOT / "signed-method-evidence"
EXPECTED_SHA256 = {
    "codec": "4ded4c97bb80255185483cb472b3fab0bcb4d2b91300365506c4804e1c52534d",
    "duration": "f3d76bd05bf650580a567dd84dd99c53a52c18b90edb6ac5a8166c53bf2bc1a8",
    "session": "a5a035503a7f6d3b3686c94f797a831c4f4984acdf2c02d3b30fd4ed3cfa5e34",
    "source": "2de654a309cf484114fa1ebf81df15ea8b2f860ceab5e21d161c4d041bb476d2",
}


def fixture(name: str) -> str:
    filenames = {
        "codec": "watch84-shape-codec-methods.dexdump.txt",
        "duration": "watch84-duration-policy-methods.dexdump.txt",
        "session": "watch84-fresh-session-methods.dexdump.txt",
        "source": "watch84-source-payload-codec-methods.dexdump.txt",
    }
    payload = (FIXTURES / filenames[name]).read_bytes()
    if hashlib.sha256(payload).hexdigest() != EXPECTED_SHA256[name]:
        raise AssertionError(f"signed fixture identity changed: {name}")
    return payload.decode("utf-8")


class Watch84SignedFixtureRepresentationTests(unittest.TestCase):
    """Every correction remains owner/method-specific and fails closed on mutation."""

    def setUp(self) -> None:
        self.codec = fixture("codec")
        self.duration = fixture("duration")
        self.session = fixture("session")
        self.source = fixture("source")

    def report(self, *, codec: str | None = None, duration: str | None = None,
               session: str | None = None, source: str | None = None) -> dict[str, bool]:
        return WATCH.watch_predicate_report(
            shape_class="",
            shape_companion="",
            shape_codec=self.codec if codec is None else codec,
            fresh_runtime="",
            fresh_session=self.session if session is None else session,
            duration_policy=self.duration if duration is None else duration,
            source_codec=self.source if source is None else source,
            runtime=b"",
        )

    def assert_predicate_fails(self, predicate: str, **replacement: str) -> None:
        with self.subTest(predicate=predicate, replacement=replacement):
            self.assertFalse(self.report(**replacement)[predicate])

    def test_exact_signed_fixture_passes_all_three_corrected_predicates(self) -> None:
        report = self.report()
        self.assertTrue(report["rawShapeCodecExactV2Kind3HeaderSlotsAndFlags"], report)
        self.assertTrue(report["closedV2DurationAndLifetime"], report)
        self.assertTrue(report["hardSampleCutoffGuardsAppend"], report)

    def test_codec_rejects_deleted_or_altered_encode_scalars_and_guards(self) -> None:
        # Encode's V2/version, kind-3, three flags, slot width, header width,
        # and unknown-flags mask are each exact instructions in the selected method.
        for old, changed in (
            ("#int 2 // #2", "#int 4 // #4"),
            ("#int 3 // #3", "#int 4 // #4"),
            ("#int 8 // #8", "#int 9 // #9"),
            ("#int 16 // #10", "#int 17 // #11"),
            ("#int 32 // #20", "#int 33 // #21"),
            ("#int 10 // #0a", "#int 11 // #0b"),
            ("#int 45 // #2d", "#int 46 // #2e"),
            ("#int -64 // #c0", "#int -63 // #c1"),
        ):
            self.assert_predicate_fails(
                "rawShapeCodecExactV2Kind3HeaderSlotsAndFlags",
                codec=self.codec.replace(old, changed, 1),
            )

    def test_codec_rejects_deleted_or_altered_decode_scalars_and_flag_guards(self) -> None:
        # Mutate decode-only instances so encode cannot satisfy the assertion.
        for old, changed in (
            ("|002a: const/4 v8, #int 2 // #2", "|002a: const/4 v8, #int 4 // #4"),
            ("|002d: const/4 v8, #int 3 // #3", "|002d: const/4 v8, #int 4 // #4"),
            ("#int -64 // #c0", "#int -63 // #c1"),
            ("and-int/lit8 v2, v8, #int 8 // #08", "and-int/lit8 v2, v8, #int 9 // #09"),
            ("and-int/lit8 v17, v8, #int 16 // #10", "and-int/lit8 v17, v8, #int 17 // #11"),
            ("and-int/lit8 v17, v8, #int 32 // #20", "and-int/lit8 v17, v8, #int 33 // #21"),
            ("rem-int/lit8 v0, v4, #int 20 // #14", "rem-int/lit8 v0, v4, #int 19 // #13"),
            ("mul-int/lit8 v17, v7, #int 10 // #0a", "mul-int/lit8 v17, v7, #int 11 // #0b"),
            ("const/16 v16, #int 45 // #2d", "const/16 v16, #int 46 // #2e"),
            ("const/16 v10, #int 4096 // #1000", "const/16 v10, #int 4095 // #0fff"),
        ):
            self.assert_predicate_fails(
                "rawShapeCodecExactV2Kind3HeaderSlotsAndFlags",
                codec=self.codec.replace(old, changed, 1),
            )

    def test_codec_rejects_boundary_call_substitution(self) -> None:
        self.assert_predicate_fails(
            "rawShapeCodecExactV2Kind3HeaderSlotsAndFlags",
            source=self.source.replace("CardiacInputShapeCodec;.encode:", "WrongCodec;.encode:", 1),
        )
        self.assert_predicate_fails(
            "rawShapeCodecExactV2Kind3HeaderSlotsAndFlags",
            source=self.source.replace("CardiacInputShapeCodec;.decode:", "WrongCodec;.decode:", 1),
        )

    def test_duration_rejects_each_closed_rung_removal_or_alteration(self) -> None:
        # Exact unsigned const-wide/32 renderings in the signed <clinit> block.
        for old, changed in (
            ("#000493e0", "#000493e1"),  # 300,000
            ("#001b7740", "#001b7741"),  # 1,800,000
            ("#006ddd00", "#006ddd01"),  # 7,200,000
            ("#01b77400", "#01b77401"),  # 28,800,000
        ):
            self.assert_predicate_fails(
                "closedV2DurationAndLifetime",
                duration=self.duration.replace(old, changed, 1),
            )

    def test_duration_rejects_rung_order_change_and_lifetime_guard_change(self) -> None:
        reordered = self.duration.replace("#000493e0", "#TEMP", 1).replace(
            "#001b7740", "#000493e0", 1,
        ).replace("#TEMP", "#001b7740", 1)
        self.assert_predicate_fails("closedV2DurationAndLifetime", duration=reordered)
        for old, changed in (
            ("Ljava/util/Set;.contains:", "Ljava/util/Set;.missing:"),
            ("if-eqz v0", "if-nez v0"),
            ("#000dbba0", "#000dbba1"),  # 900,000 grace
            ("add-long/2addr", "sub-long/2addr"),
            ("return-wide", "return-void"),
            ("Unadmitted fresh V2 duration", "REMOVED"),
        ):
            self.assert_predicate_fails(
                "closedV2DurationAndLifetime",
                duration=self.duration.replace(old, changed, 1),
            )

    def test_hard_cutoff_rejects_each_cutoff_guard_and_append_path_change(self) -> None:
        # Exact acceptsSamples guard chain: STARTED -> start -> duration -> compare -> reject-at-or-after.
        for old, changed in (
            ("FreshRunPhase;.STARTED:", "FreshRunPhase;.FINALIZED:"),
            ("FreshRunReceipt;.getStartElapsedMs:", "FreshRunReceipt;.getFinalizedElapsedMs:"),
            ("FreshRunReceipt;.getDurationMs:", "FreshRunReceipt;.getLifetimeMs:"),
            ("cmp-long v0, v0, v2", "cmp-long v1, v1, v2"),
            ("if-gez v0, 0048", "if-gtz v0, 0048"),
        ):
            self.assert_predicate_fails(
                "hardSampleCutoffGuardsAppend",
                session=self.session.replace(old, changed, 1),
            )
        for old, changed in (
            ("FreshRunSession;.acceptsSamples:()Z", "FreshRunSession;.permitsDelivery:()Z"),
            ("if-nez v0, 0014", "if-eqz v0, 0014"),
            ("return-object v1", "return-object v0"),
            ("SourceJournal;.append:", "SourceJournal;.appendUnchecked:"),
        ):
            self.assert_predicate_fails(
                "hardSampleCutoffGuardsAppend",
                session=self.session.replace(old, changed, 1),
            )

    def test_hard_cutoff_rejects_guard_or_append_reordering(self) -> None:
        # Move the sole duration getter before STARTED/start: same tokens, wrong guard order.
        duration_getter = "FreshRunReceipt;.getDurationMs:()J"
        reordered_guard = self.session.replace(duration_getter, "REMOVED", 1)
        reordered_guard = reordered_guard.replace(
            "FreshRunPhase;.STARTED:", duration_getter + "\nFreshRunPhase;.STARTED:", 1,
        )
        self.assert_predicate_fails("hardSampleCutoffGuardsAppend", session=reordered_guard)

        # Move the sole journal append before acceptsSamples: same tokens, unsafe execution order.
        journal_append = "SourceJournal;.append:"
        reordered_append = self.session.replace(journal_append, "REMOVED", 1)
        reordered_append = reordered_append.replace(
            "FreshRunSession;.acceptsSamples:()Z", journal_append + "\nFreshRunSession;.acceptsSamples:()Z", 1,
        )
        self.assert_predicate_fails("hardSampleCutoffGuardsAppend", session=reordered_append)


if __name__ == "__main__":
    unittest.main(verbosity=2)
