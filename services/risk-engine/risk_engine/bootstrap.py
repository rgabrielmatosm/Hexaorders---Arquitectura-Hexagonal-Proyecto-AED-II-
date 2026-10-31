"""Composition root: only this module chooses concrete adapters and use cases."""

from risk_engine.adapters.http import create_app
from risk_engine.application.score_risk import ScoreRisk

app = create_app(ScoreRisk())
