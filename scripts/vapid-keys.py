"""
웹 푸시(VAPID) 키 한 쌍 만들기. 한 번 만들어 서버 환경 변수에 넣고 계속 쓴다 (바꾸면 모든 브라우저가 다시 구독해야 함).

    python scripts/vapid-keys.py

출력:
    VAPID_PUBLIC_KEY=...   (65바이트 공개 키, base64url — 브라우저에 그대로 줘도 된다)
    VAPID_PRIVATE_KEY=...  (32바이트 개인 키, base64url — 비밀. git 에 올리지 않는다)

로컬은 apps/server/.env.local, 운영은 deploy/.env 에 붙여 넣는다.
필요: pip install cryptography
"""
import base64

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode()


key = ec.generate_private_key(ec.SECP256R1())
private = key.private_numbers().private_value.to_bytes(32, 'big')
public = key.public_key().public_bytes(serialization.Encoding.X962, serialization.PublicFormat.UncompressedPoint)
print(f'VAPID_PUBLIC_KEY={b64url(public)}')
print(f'VAPID_PRIVATE_KEY={b64url(private)}')
