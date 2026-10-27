#!/usr/bin/env python3
"""Read-only verification of a user-uploaded native diagnostic text report.

Only the supplied file is read. This verifies exported-body checksum consistency
and local event structure; it cannot reopen the device envelope, prove a signer,
join a Watch transaction, establish canonical custody or diagnose radio cause.
Outputs are private diagnostic derivatives, never production source or GitHub data.
"""
from pathlib import Path
import argparse
import hashlib
import json
import re


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def reject_constant(value):
    raise ValueError("NON_FINITE_JSON_CONSTANT_" + value)


def integer(value):
    return type(value) is int


def slot_value(slot):
    require(type(slot) is dict and slot.get("state") in {"VALUE", "ABSENT", "INVALID", "UNKNOWN"}, "INVALID_SLOT")
    return slot["value"] if slot["state"] == "VALUE" else None


def verify(path):
    data = path.read_bytes()
    require(len(data) <= 256 * 1024, "REPORT_SIZE_BOUND")
    require(b"\r" not in data, "UNEXPECTED_NEWLINE_REPRESENTATION")
    lines = data.splitlines()
    require(lines and lines[0] == b"HUGR SAVED RECEIPT DIAGNOSTICS", "REPORT_HEADER")
    require(lines[-1] == b"END REPORT", "REPORT_TERMINATOR")
    raw_bodies = [line for line in lines if line.startswith(b"{")]
    declared_hashes = [line.split(b"=", 1)[1].decode("ascii") for line in lines if line.startswith(b"body_sha256=")]
    count_lines = [line for line in lines if line.startswith(b"saved_incident_count=")]
    require(len(count_lines) == 1, "INCIDENT_COUNT_DECLARATION")
    count = int(count_lines[0].split(b"=", 1)[1])
    require(0 < count <= 6 and count == len(raw_bodies) == len(declared_hashes), "INCIDENT_COUNT_OR_BOUND")
    require(b"complete_reports=YES" in lines, "COMPLETE_REPORTS_DECLARATION")
    require(b"mode=ISOLATED_NATIVE_RECEIPT_DIAGNOSTIC_READER" in lines, "READER_MODE")
    require(lines.count(b"envelope_integrity=VERIFIED") == count, "ENVELOPE_DECLARATION_COUNT")
    require(lines.count(b"commit_state=ACKNOWLEDGED_ATOMIC_PUBLICATION_CHECKSUM_REOPEN_VERIFIED") == count, "COMMIT_DECLARATION_COUNT")
    records = []
    for index, (raw_body, declared_hash) in enumerate(zip(raw_bodies, declared_hashes)):
        require(re.fullmatch("[0-9a-f]{64}", declared_hash), "INVALID_BODY_HASH")
        digest = hashlib.sha256(raw_body).hexdigest()
        require(digest == declared_hash, "EXPORTED_BODY_HASH_MISMATCH")
        body = json.loads(raw_body.decode("utf-8"), object_pairs_hook=unique_object, parse_constant=reject_constant)
        require(body.get("format") == "HUGR_PHONE_RECEIPT_INCIDENT_V3", "UNEXPECTED_FORMAT")
        require(body.get("nativeCorrelation") == "CORRELATION_UNRESOLVED", "CORRELATION_SCOPE")
        operations = body["operations"]
        require(type(operations) is list and len(operations) <= body["operationLimit"] == 64, "OPERATION_CAPACITY")
        ordinals = [row["localOrdinal"] for row in operations]
        require(all(integer(value) for value in ordinals) and ordinals == sorted(set(ordinals)), "OPERATION_ORDER_OR_DUPLICATE")
        context = body["diagnosticContext"]
        first = context["firstCause"]
        before = context["preceding"]
        after = context["subsequent"]
        require(type(before) is list and len(before) <= 16 and type(after) is list and len(after) <= 16, "CONTEXT_BOUND")
        events = before + [first] + after
        event_ordinals = [event["ordinal"] for event in events]
        require(all(integer(value) for value in event_ordinals) and event_ordinals == sorted(set(event_ordinals)), "EVENT_ORDER_OR_DUPLICATE")
        times = [slot_value(event["atMonotonicMs"]) for event in events]
        require(all(integer(value) for value in times) and times == sorted(times), "LOCAL_MONOTONIC_ORDER")
        operation_ordinal = slot_value(first["operationOrdinal"])
        matches = [op for op in operations if op["localOrdinal"] == operation_ordinal]
        require(len(matches) == 1, "FIRST_CAUSE_OPERATION_BINDING")
        failed = matches[0]
        first_time = slot_value(first["atMonotonicMs"])
        require(first["outcome"] == "DEADLINE" and first["origin"] == "TIMEOUT" and body["firstFailure"] == "DEADLINE", "FIRST_FAILURE_KIND")
        require(failed["operationKind"] == "R1_WRITE" and failed["pageOffset"] == 0 and failed["deadlineObserved"] is True, "FAILED_SUBSTEP")
        require(first_time == body["firstFailureAtMonotonicMs"] == failed["settledAtMonotonicMs"], "DEADLINE_TIME_BINDING")
        start = failed["jsSubmittedAtMonotonicMs"]
        require(integer(start) and start <= first_time, "FAILED_SUBMISSION_TIME")
        cancel = [event for event in after if event["stage"] == "GUARD_CANCELLATION" and event["outcome"] == "CANCEL_REQUESTED" and slot_value(event["operationOrdinal"]) == operation_ordinal]
        late = [event for event in after if event["outcome"] == "LATE_REJECTION_IGNORED" and slot_value(event["operationOrdinal"]) == operation_ordinal]
        disconnect = [event for event in after if event["stage"] == "DISCONNECT_CALLBACK"]
        require(cancel and late and disconnect, "EXPECTED_SUBSEQUENT_EVIDENCE")
        require(first["ordinal"] < cancel[0]["ordinal"] < late[0]["ordinal"] < disconnect[0]["ordinal"], "CAUSE_CANCELLATION_DISCONNECT_ORDER")
        successful_appends = [event for event in before if event["stage"] == "NATIVE_APPEND" and event["outcome"] == "SUCCEEDED"]
        retained_successes = [op for op in operations if op.get("timelySettlement") == "FULFILLED"]
        retained_acks = [op for op in retained_successes if op["operationKind"] == "SOURCE_ACK"]
        timeline = []
        for event in events:
            stamp = slot_value(event["atMonotonicMs"])
            timeline.append({"eventOrdinal": event["ordinal"], "offsetFromWriteSubmissionMs": stamp - start, "offsetFromDeadlineMs": stamp - first_time, "stage": event["stage"], "outcome": event["outcome"], "origin": event["origin"], "operationOrdinal": event["operationOrdinal"], "error": event["error"]})
        publication = body["publication"]
        acknowledgement = publication["acknowledgement"]
        require(acknowledgement["revision"] <= body["revision"] and acknowledgement["incidentPseudonym"] == body["incidentPseudonym"], "ACK_METADATA_BINDING")
        records.append({
            "incidentIndex": index,
            "format": body["format"],
            "bodySha256Declared": declared_hash,
            "bodySha256Computed": digest,
            "bodyBytes": len(raw_body),
            "bodyChecksumMatches": True,
            "reportedEnvelopeIntegrity": "VERIFIED",
            "reportedCurrentCommitState": "ACKNOWLEDGED_ATOMIC_PUBLICATION_CHECKSUM_REOPEN_VERIFIED",
            "firstCause": first,
            "failedOperation": failed,
            "writeDeadlineElapsedMs": first_time - start,
            "lastNativeAppendOffsetFromWriteStartMs": slot_value(successful_appends[-1]["atMonotonicMs"]) - start if successful_appends else None,
            "lastNativeAppendBeforeDeadlineMs": first_time - slot_value(successful_appends[-1]["atMonotonicMs"]) if successful_appends else None,
            "cancelAfterDeadlineMs": slot_value(cancel[0]["atMonotonicMs"]) - first_time,
            "lateRejectionAfterDeadlineMs": slot_value(late[0]["atMonotonicMs"]) - first_time,
            "disconnectCallbackAfterDeadlineMs": slot_value(disconnect[0]["atMonotonicMs"]) - first_time,
            "lateRejectionError": late[0]["error"],
            "retainedOperationCount": len(operations),
            "retainedFulfilledOperationCount": len(retained_successes),
            "retainedFulfilledSourceAckCount": len(retained_acks),
            "contextOverflow": context["contextOverflow"],
            "operationOverflow": body["overflow"],
            "publicationMetadata": publication,
            "timeline": timeline,
            "noWholeRunCustodyOrWatchAcceptanceClaim": True,
            "noSourceRunIdentityAvailableInPayloadFreeBody": True,
            "noNativeDispatchOrCrossDeviceCorrelationClaim": True,
        })
    return {
        "status": "PASS_UPLOADED_REPORT_BODY_CHECKSUM_AND_LOCAL_STRUCTURE",
        "inputPath": str(path),
        "inputBytes": len(data),
        "inputSha256": hashlib.sha256(data).hexdigest(),
        "incidentCount": count,
        "nativePublicationLossDeclaration": "NONE_OBSERVED" if b"publication_loss_state=NONE_OBSERVED" in lines else "OTHER",
        "nativeDeviceEnvelopeNotIncludedCannotIndependentlyReopenIt": True,
        "externalDeviceArtifactSignerNotEstablishedByThisChecksum": True,
        "localSaveShareRouteAndOfflineReopenNotInReport": True,
        "records": records,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    require(not args.output.exists(), "OUTPUT_ALREADY_EXISTS_STOP")
    result = verify(args.input)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(result["status"])
    print("Exact uploaded bytes and exported JSON-body checksum checked; local ordering only.")
    print("No device action, envelope reconstruction, source change, native dispatch or recording acceptance.")


if __name__ == "__main__":
    main()
