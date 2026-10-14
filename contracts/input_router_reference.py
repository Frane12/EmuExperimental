"""Reference touch ownership model, for architecture tests only.

This is not an Android input driver and does not emit input to Wine.
"""
class InputRouter:
    def __init__(self, left_zone_fraction=0.34):
        assert 0.0 < left_zone_fraction < 1.0
        self.left_zone_fraction = left_zone_fraction
        self._owners = {}

    def pointer_down(self, pointer_id, normalized_x):
        if pointer_id in self._owners:
            raise ValueError("Pointer ID already owned")
        if not (0.0 <= normalized_x <= 1.0):
            raise ValueError("X must be normalized to [0,1]")
        owner = "virtual-stick" if normalized_x < self.left_zone_fraction else "relative-mouse"
        self._owners[pointer_id] = owner
        return owner

    def pointer_move(self, pointer_id, normalized_x):
        # Do NOT reroute a pointer because it crosses the virtual zone boundary.
        return self._owners.get(pointer_id)

    def pointer_up(self, pointer_id):
        return self._owners.pop(pointer_id, None)

    def cancel_all(self):
        self._owners.clear()

    def owners(self):
        return dict(self._owners)
