{{/* Database env (DB_URL/USERNAME/PASSWORD) — shared by api/worker/scheduler/flyway. */}}
{{- define "digishield.dbEnv" -}}
- name: DB_URL
  value: {{ .Values.database.url | quote }}
- name: DB_USERNAME
  value: {{ .Values.database.username | quote }}
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Values.database.existingSecret }}
      key: {{ .Values.database.passwordKey }}
{{- end -}}

{{/* OAuth2 resource-server issuer — shared by api/worker/scheduler/flyway. The
     app boots a JwtDecoder from this OIDC issuer (e.g. a Cognito user pool). */}}
{{- define "digishield.authEnv" -}}
{{- with .Values.auth.issuerUri }}
{{- /* The app's SecurityConfig reads digishield.auth.jwt.issuer-uri, bound to
       AUTH_JWT_ISSUER_URI in application.yml — set that, not the Spring default. */}}
- name: AUTH_JWT_ISSUER_URI
  value: {{ . | quote }}
{{- end }}
{{- end -}}

{{/* AI (Claude) env — shared by api/worker/scheduler. Off by default (StubAiClient).
     When enabled, ANTHROPIC_API_KEY is injected from a Secret (never values). All
     app instances load the AI beans, so every workload that enables Claude needs
     the key. */}}
{{- define "digishield.aiEnv" -}}
{{- if .Values.ai.claude.enabled }}
- name: AI_CLAUDE_ENABLED
  value: "true"
{{- if .Values.ai.claude.existingSecret }}
- name: ANTHROPIC_API_KEY
  valueFrom:
    secretKeyRef:
      name: {{ .Values.ai.claude.existingSecret }}
      key: {{ .Values.ai.claude.apiKeyKey }}
{{- end }}
{{- end }}
{{- end -}}

{{/* RabbitMQ env — shared by api/worker/scheduler. Only rendered when a host is
     configured (in-cluster broker on k3s/on-prem); credentials come from a
     Secret when one is set. */}}
{{- define "digishield.rabbitEnv" -}}
{{- if .Values.rabbitmq.host }}
- name: SPRING_RABBITMQ_HOST
  value: {{ .Values.rabbitmq.host | quote }}
- name: SPRING_RABBITMQ_PORT
  value: {{ .Values.rabbitmq.port | quote }}
{{- if .Values.rabbitmq.existingSecret }}
- name: SPRING_RABBITMQ_USERNAME
  valueFrom:
    secretKeyRef:
      name: {{ .Values.rabbitmq.existingSecret }}
      key: {{ .Values.rabbitmq.usernameKey }}
- name: SPRING_RABBITMQ_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Values.rabbitmq.existingSecret }}
      key: {{ .Values.rabbitmq.passwordKey }}
{{- end }}
{{- end }}
{{- end -}}

{{/* Redis env — shared by api/worker/scheduler (REDIS_PASSWORD only when a secret is set). */}}
{{- define "digishield.redisEnv" -}}
- name: REDIS_HOST
  value: {{ .Values.redis.host | quote }}
- name: REDIS_PORT
  value: {{ .Values.redis.port | quote }}
- name: REDIS_SSL
  value: {{ .Values.redis.tls | quote }}
{{- if .Values.redis.existingSecret }}
- name: REDIS_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ .Values.redis.existingSecret }}
      key: {{ .Values.redis.passwordKey }}
{{- end }}
{{- end -}}
