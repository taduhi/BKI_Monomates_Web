import unittest

from monomates_bridge import ScanCommand, physical_reply, result_payload


class ResultPayloadTest(unittest.TestCase):
    command = ScanCommand("00000000-0000-0000-0000-000000000001", "now")

    def test_configured_direction_is_accepted(self):
        payload = result_payload(
            {"ok": True, "decision": "RIGHT", "confidence": 0.95, "label": "bottle"},
            self.command,
            "RIGHT",
        )
        self.assertEqual(payload["itemType"], "CLEAR_PET_BOTTLE")
        self.assertEqual(payload["weightChangeGrams"], 24.0)

    def test_opposite_direction_is_not_accepted(self):
        payload = result_payload(
            {"ok": True, "decision": "LEFT", "confidence": 0.95, "label": "not_bottle"},
            self.command,
            "RIGHT",
        )
        self.assertIsNone(payload["itemType"])
        self.assertEqual(payload["weightChangeGrams"], 0.0)

    def test_hardware_error_is_invalid_not_not_accepted(self):
        payload = result_payload(
            {"ok": False, "decision": "ERROR", "error": "CAMERA"},
            self.command,
            "RIGHT",
        )
        self.assertIsNone(payload["itemType"])
        self.assertEqual(payload["weightChangeGrams"], 24.0)

    def test_low_confidence_is_invalid(self):
        payload = result_payload(
            {"ok": True, "decision": "LEFT", "confidence": 0.40, "label": "not_bottle"},
            self.command,
            "RIGHT",
        )
        self.assertIsNone(payload["itemType"])
        self.assertEqual(payload["weightChangeGrams"], 24.0)

    def test_physical_directions_can_be_swapped_independently(self):
        self.assertEqual(physical_reply("DECISION|LEFT", True), "DECISION|RIGHT")
        self.assertEqual(physical_reply("DECISION|RIGHT", True), "DECISION|LEFT")
        self.assertEqual(physical_reply("ERROR|CAMERA", True), "ERROR|CAMERA")


if __name__ == "__main__":
    unittest.main()
