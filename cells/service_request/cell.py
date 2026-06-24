from __future__ import annotations
from typing import Any
from langgraph.graph import StateGraph

class Cell:
    def __init__(self) -> None:
        self.graph: StateGraph[dict[str, Any]] | None = None

    def _init(self, state: dict[str, Any]) -> dict[str, Any]:
        return {"utility_state": {"phase": "init", "projectId": state.get("projectId", "unknown"), "completionPct": 0}, "next_node": "process"}

    def _process(self, state: dict[str, Any]) -> dict[str, Any]:
        return {"utility_state": {**state.get("utility_state", {}), "phase": "complete", "completionPct": 100}, "next_node": "end"}

    def _build_graph(self) -> StateGraph[dict[str, Any]]:
        graph = StateGraph(dict)
        graph.add_node("init", self._init)
        graph.add_node("process", self._process)
        graph.add_edge("init", "process")
        graph.add_edge("process", "__end__")
        graph.set_entry_point("init")
        return graph.compile()

    def solve(self, state: dict[str, Any]) -> dict[str, Any]:
        if self.graph is None:
            self.graph = self._build_graph()
        return self.graph.invoke(state)
