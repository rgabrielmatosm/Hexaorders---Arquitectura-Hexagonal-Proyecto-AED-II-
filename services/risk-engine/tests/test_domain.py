from decimal import Decimal

import pytest

from risk_engine.application.score_risk import ScoreRisk
from risk_engine.domain.risk import Decision, Evaluation


@pytest.mark.parametrize(
    "amount, score, decision",
    [
        ("0.01", 10, Decision.APPROVED),
        ("499.99", 10, Decision.APPROVED),
        ("500.00", 40, Decision.APPROVED),
        ("999.99", 40, Decision.APPROVED),
        ("1000.00", 75, Decision.REVIEW),
        ("1000000", 75, Decision.REVIEW),
    ],
)
def test_risk_thresholds(amount, score, decision):
    result = ScoreRisk().execute(Evaluation("client_01", Decimal(amount)))
    assert (result.score, result.decision) == (score, decision)


@pytest.mark.parametrize("amount", ["0", "-1", "1000000.01", "0.001", "1.000", "NaN", "sNaN", "Infinity", "-Infinity"])
def test_domain_rejects_invalid_money(amount):
    with pytest.raises(ValueError):
        Evaluation("client_01", Decimal(amount))


@pytest.mark.parametrize("customer", ["", "a" * 65, "customer name", "á", "client\n", None, 12])
def test_domain_rejects_invalid_customer(customer):
    with pytest.raises(ValueError):
        Evaluation(customer, Decimal("1.00"))


def test_domain_requires_decimal_instead_of_binary_float():
    with pytest.raises(ValueError):
        Evaluation("client_01", 0.1)
