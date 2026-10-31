from risk_engine.domain.risk import Evaluation, RiskResult, assess


class ScoreRisk:
    """Stateless use case: this service needs no persistence or outbound I/O."""

    def execute(self, evaluation: Evaluation) -> RiskResult:
        return assess(evaluation)
