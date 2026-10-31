import pytest
from fastapi.testclient import TestClient

from risk_engine.adapters.http import create_app
from risk_engine.application.score_risk import ScoreRisk


@pytest.fixture
def client():
    with TestClient(create_app(ScoreRisk())) as test_client:
        yield test_client


@pytest.mark.parametrize("amount", ["1500.00", 1500.0, 1500])
def test_score_accepts_decimal_string_and_number(client, amount):
    response = client.post("/score", json={"customerId": "customer_01", "amount": amount})
    assert response.status_code == 200
    assert response.json() == {"score": 75, "decision": "REVIEW"}


@pytest.mark.parametrize(
    "payload",
    [
        {},
        {"customerId": "client"},
        {"customerId": "client", "amount": "0"},
        {"customerId": "client", "amount": "-1"},
        {"customerId": "client", "amount": "1000000.01"},
        {"customerId": "client", "amount": "1.001"},
        {"customerId": "client", "amount": "1.000"},
        {"customerId": "client", "amount": "NaN"},
        {"customerId": "client", "amount": "Infinity"},
        {"customerId": "client", "amount": "text"},
        {"customerId": "client", "amount": True},
        {"customerId": "client", "amount": None},
        {"customerId": "client", "amount": []},
        {"customerId": "client", "amount": {}},
        {"customerId": "", "amount": "1"},
        {"customerId": "a" * 65, "amount": "1"},
        {"customerId": "hello world", "amount": "1"},
        {"customerId": "á", "amount": "1"},
        {"customerId": "client\n", "amount": "1"},
        {"customerId": 12, "amount": "1"},
        {"customerId": "client", "amount": "1", "currency": "PEN"},
        ["client", "1"],
    ],
)
def test_invalid_requests_return_422(client, payload):
    assert client.post("/score", json=payload).status_code == 422


@pytest.mark.parametrize(
    "body",
    [
        '{"customerId":"client","amount":1.00000000000000000000001}',
        '{"customerId":"client","amount":999.999999999999999999999}',
        '{"customerId":"client","amount":NaN}',
        '{"customerId":"client","amount":Infinity}',
        '{bad json',
        '',
    ],
)
def test_json_parser_never_silently_rounds_or_accepts_non_json_numbers(client, body):
    assert client.post("/score", content=body, headers={"Content-Type": "application/json"}).status_code == 422


def test_rejects_unexpected_media_type(client):
    assert client.post("/score", content='{"customerId":"client","amount":1}').status_code == 415


def test_health_and_observability_match_business_outcomes(client):
    for path in ("/health/live", "/health/ready"):
        assert client.get(path).json() == {"status": "UP"}
    client.post("/score", json={"customerId": "client", "amount": "100"})
    client.post("/score", json={"customerId": "client", "amount": "1000"})
    client.post("/score", json={"customerId": "client", "amount": "0"})
    metrics = client.get("/metrics")
    assert metrics.status_code == 200
    assert 'risk_evaluations_total{decision="APPROVED"} 1.0' in metrics.text
    assert 'risk_evaluations_total{decision="REVIEW"} 1.0' in metrics.text
    assert "risk_evaluation_duration_seconds_count 2.0" in metrics.text


def test_openapi_exposes_input_and_output_contract(client):
    operation = client.get("/openapi.json").json()["paths"]["/score"]["post"]
    assert operation["requestBody"]["content"]["application/json"]["schema"]["required"] == ["customerId", "amount"]
    assert "200" in operation["responses"]
