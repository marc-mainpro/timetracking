#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"

python3 - <<'PY'
import json
import os
import urllib.request

base = os.environ.get('API_BASE_URL', 'http://localhost:8080').rstrip('/')


def post_json(path, payload, token=None):
    req = urllib.request.Request(
        f"{base}{path}",
        data=json.dumps(payload).encode(),
        headers={
            'Content-Type': 'application/json',
            **({'Authorization': f'Bearer {token}'} if token else {})
        },
        method='POST',
    )
    with urllib.request.urlopen(req) as response:
        return json.loads(response.read().decode()) if response.readable() else {}


def login(email, password):
    response = post_json('/api/v1/auth/login', {'email': email, 'password': password})
    return response['accessToken']


tenants = [
    ('demo-a', 'Tenant Demo A', 'admin-a@acme.test', 'employee-a@acme.test'),
    ('demo-b', 'Tenant Demo B', 'admin-b@acme.test', 'employee-b@acme.test'),
]

# Los tenants de demo se crean desde plataforma: es la única vía que deja un
# tenant operativo sin pasar por la aprobación de una solicitud, y exige
# PLATFORM_ADMIN_EMAIL/PLATFORM_ADMIN_PASSWORD en el entorno.
platform_email = os.environ.get('PLATFORM_ADMIN_EMAIL')
platform_password = os.environ.get('PLATFORM_ADMIN_PASSWORD')
if not platform_email or not platform_password:
    raise SystemExit(
        'Define PLATFORM_ADMIN_EMAIL y PLATFORM_ADMIN_PASSWORD: los tenants se crean desde plataforma.')
platform_token = login(platform_email, platform_password)

for seed, tenant_name, admin_email, employee_email in tenants:
    post_json('/api/v1/platform/tenants', {
        'tenantName': tenant_name,
        'timezone': 'Europe/Madrid',
        'adminEmail': admin_email,
        'adminPassword': 'supersecretpwd',
        'firstName': 'Admin',
        'lastName': seed,
    }, token=platform_token)
    admin_token = login(admin_email, 'supersecretpwd')
    post_json('/api/v1/employees', {
        'email': employee_email,
        'password': 'employeepwd123',
        'firstName': 'Employee',
        'lastName': seed,
        'roles': ['EMPLOYEE'],
    }, token=admin_token)

print('Datos de demo creados: 2 tenants, 2 admins y 2 empleados.')
PY
