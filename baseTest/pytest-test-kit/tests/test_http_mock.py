"""responses 库示例：真实网络拦截版的 Mock。

InMemoryMockEngine 只记录场景；要给真实 HTTP 调用打桩，
Python 侧通常用 responses（对应 Java 接入 WireMock）。
"""

import requests
import responses


class TestHttpMock:
    @responses.activate
    def test_get_order_is_stubbed(self):
        responses.add(
            responses.GET,
            "http://demo.local/api/orders/ORD-1",
            json={"code": 0, "orderId": "ORD-1", "totalPrice": 180},
            status=200,
        )

        reply = requests.get("http://demo.local/api/orders/ORD-1", timeout=5)

        assert reply.status_code == 200
        assert reply.json()["totalPrice"] == 180

    @responses.activate
    def test_call_count_can_be_verified(self):
        responses.add(
            responses.POST,
            "http://demo.local/api/orders",
            json={"code": 0},
            status=200,
        )

        requests.post("http://demo.local/api/orders", json={"quantity": 1}, timeout=5)
        requests.post("http://demo.local/api/orders", json={"quantity": 2}, timeout=5)

        assert len(responses.calls) == 2
