"""Deterministic, explainable demo risk policy; no framework dependencies."""

from dataclasses import dataclass
from decimal import Decimal
from enum import StrEnum
import re


class Decision(StrEnum):
    APPROVED = "APPROVED"
    REVIEW = "REVIEW"


@dataclass(frozen=True, slots=True)
class Evaluation:
    customer_id: str
    amount: Decimal

    def __post_init__(self) -> None:
        if not isinstance(self.customer_id, str) or not re.fullmatch(
            r"[A-Za-z0-9_-]{1,64}", self.customer_id
        ):
            raise ValueError("customerId must match [A-Za-z0-9_-]{1,64}")
        if not isinstance(self.amount, Decimal) or not self.amount.is_finite():
            raise ValueError("amount must be a finite Decimal")
        if not Decimal("0") < self.amount <= Decimal("1000000"):
            raise ValueError("amount must be greater than 0 and at most 1000000")
        if self.amount.as_tuple().exponent < -2:
            raise ValueError("amount must have at most two decimal places")


@dataclass(frozen=True, slots=True)
class RiskResult:
    score: int
    decision: Decision


def assess(evaluation: Evaluation) -> RiskResult:
    """Demo rules, deliberately transparent for the university presentation."""
    amount = evaluation.amount
    score = 75 if amount >= Decimal("1000") else 40 if amount >= Decimal("500") else 10
    return RiskResult(score, Decision.REVIEW if score >= 70 else Decision.APPROVED)
