from typing import Protocol

from risk_engine.domain.risk import Evaluation, RiskResult


class ScoreRiskPort(Protocol):
    """Driving port: HTTP and tests invoke the same business capability."""

    def execute(self, evaluation: Evaluation) -> RiskResult: ...
