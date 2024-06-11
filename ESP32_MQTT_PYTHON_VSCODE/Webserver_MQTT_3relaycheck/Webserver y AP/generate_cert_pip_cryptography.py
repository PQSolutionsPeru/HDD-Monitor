from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization
from datetime import datetime, timedelta

# Generar la clave privada
private_key = rsa.generate_private_key(
    public_exponent=65537,
    key_size=2048,
)

# Guardar la clave privada en un archivo
with open("key.pem", "wb") as f:
    f.write(private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.TraditionalOpenSSL,
        encryption_algorithm=serialization.NoEncryption(),
    ))

# Generar un certificado autofirmado
subject = issuer = x509.Name([
    x509.NameAttribute(NameOID.COUNTRY_NAME, u"PE"),
    x509.NameAttribute(NameOID.STATE_OR_PROVINCE_NAME, u"Lima"),
    x509.NameAttribute(NameOID.LOCALITY_NAME, u"Lima"),
    x509.NameAttribute(NameOID.ORGANIZATION_NAME, u"HDD Fire and Technology"),
    x509.NameAttribute(NameOID.COMMON_NAME, u"localhost"),
])
cert = x509.CertificateBuilder().subject_name(
    subject
).issuer_name(
    issuer
).public_key(
    private_key.public_key()
).serial_number(
    x509.random_serial_number()
).not_valid_before(
    datetime.utcnow()
).not_valid_after(
    # Certificado válido por 365 días
    datetime.utcnow() + timedelta(days=365)
).add_extension(
    x509.SubjectAlternativeName([x509.DNSName(u"localhost")]),
    critical=False,
).sign(private_key, hashes.SHA256())

# Guardar el certificado en un archivo
with open("cert.pem", "wb") as f:
    f.write(cert.public_bytes(serialization.Encoding.PEM))
