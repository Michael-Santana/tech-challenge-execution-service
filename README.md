# Execution Service

Microsservico de Execucao e Producao da oficina. Gerencia a etapa de diagnostico e a fila FIFO de reparos aprovados, persiste seu proprio historico operacional no MongoDB e troca eventos com OS e Billing via RabbitMQ. O servico nao acessa o banco de nenhum outro microsservico.

## Tecnologias e dados

- Java 21 / Spring Boot 3.4;
- MongoDB exclusivo (`execution_service`), provisionado pelo repositorio de infraestrutura;
- RabbitMQ para receber `ORDER_CREATED` do OS Service e `PAYMENT_APPROVED` do Billing Service;
- Outbox embutida no documento Mongo da OS: transicao e evento pendente sao gravados juntos; um dispatcher envia com publisher confirms e tenta novamente ate confirmar;
- Consumidores identificam eventos repetidos por `eventId` (ou uma chave deterministica de fallback), e mensagens invalidas vao para a DLQ `execution.events.dlq` depois de cinco tentativas;
- Actuator/Prometheus em `/api/v1/actuator/prometheus`.

O documento por OS armazena status, diagnostico, reparos, timestamps, IDs de eventos de entrada ja processados e eventos de saida ainda nao confirmados. O Mongo e usado por decisao da infraestrutura compartilhada, que provisiona uma instancia exclusiva para este servico.

## Fluxo operacional

1. A abertura da OS publica `ORDER_CREATED` no exchange `os.exchange`; o Execution Service cria a ficha `WAITING_FOR_DIAGNOSIS`.
2. A oficina inicia o diagnostico e registra o resultado. O status vai para `WAITING_FOR_APPROVAL`. A integracao com o Billing para gerar o orcamento ainda precisa ser implementada.
3. Ao receber `PAYMENT_APPROVED` no exchange `billing.exchange`, a OS entra na fila FIFO `QUEUED_FOR_REPAIR`. O pagamento pode chegar antes do diagnostico; nesse caso fica registrado e a OS entra na fila assim que o diagnostico for salvo.
4. A oficina inicia os reparos, registra cada reparo realizado e conclui a execucao. O servico publica os status `EM_DIAGNOSTICO`, `AGUARDANDO_APROVACAO`, `EM_EXECUCAO` e `FINALIZADA` no exchange `os.exchange`, chave `execution.status`. O OS Service deve consumir esses eventos para atualizar seu estado.

O fluxo e iniciado por eventos e operacoes da oficina; nenhuma chamada direta entre bancos ou chamada REST sincronica para OS/Billing e necessaria. A outbox evita perder notificacoes quando o processo cai depois de gravar uma alteracao local.

O Execution recebe `BUDGET_REJECTED` na chave `billing.budget_rejected` e
`STATUS_UPDATED` com `newStatus` `CANCELADA` na chave `os.event`. Esses eventos
bloqueiam novos reparos. Se o pagamento ja foi aprovado ou chegar depois do
cancelamento, a consulta de execucao retorna `refundRequired: true` e registra
um alerta. Isso sinaliza o trabalho manual, mas **nao executa nem registra o
estorno no Mercado Pago**. Cancelamento de reparo ja concluido e enviado para
a DLQ para tratamento manual. O controle atual de concorrencia e por processo:
nao execute multiplas replicas ate implementar controle concorrente no MongoDB.

## API

Base URL: `http://localhost:8083/api/v1`; Swagger UI: `/swagger-ui.html`.

| Metodo | Rota | Funcao |
| --- | --- | --- |
| `GET` | `/executions/queue` | Lista reparos aprovados em ordem de entrada, com posicao na fila |
| `GET` | `/executions/{orderId}` | Consulta status, diagnostico, reparos e timestamps |
| `POST` | `/executions/{orderId}/diagnosis/start` | Inicia diagnostico e comunica `EM_DIAGNOSTICO` |
| `POST` | `/executions/{orderId}/diagnosis` | Registra `{"diagnosis":"..."}` e comunica `AGUARDANDO_APROVACAO` |
| `POST` | `/executions/{orderId}/repairs/start` | Inicia reparos apos pagamento confirmado e comunica `EM_EXECUCAO` |
| `POST` | `/executions/{orderId}/repairs` | Registra `{"description":"..."}` para um reparo realizado |
| `POST` | `/executions/{orderId}/complete` | Conclui a OS (exige pelo menos um reparo) e comunica `FINALIZADA` |

Transicoes fora de ordem retornam HTTP 409; OS ainda nao recebida retorna HTTP 404; campos de diagnostico e reparo vazios retornam HTTP 400. O OS Service deve ser configurado para consumir o exchange e a chave de saida (`os.exchange` / `execution.status`).

## Desenvolvimento local

Requer Docker Desktop e Java 21. Suba MongoDB e RabbitMQ:

```powershell
docker compose up --build
```

O servico fica em `http://localhost:8083/api/v1`. Para executar testes e gerar o relatorio JaCoCo (gate de 80% de cobertura de linhas):

```powershell
.\mvnw.cmd -B verify
```

Para inspecionar eventos e filas, acesse o RabbitMQ Management em `http://localhost:15672` (`guest` / `guest`). O relatorio de cobertura fica em `target/site/jacoco/index.html`.

## Kubernetes e CI

Os manifestos em `k8s/` usam o namespace `execution-service`, o MongoDB e os Secrets criados pelo repositorio `tech-challenge-infrastructure`, e o RabbitMQ no namespace `messaging`. A imagem publicada deve estar disponivel em `ghcr.io/michael-santana/tech-challenge-execution-service`. A ServiceMonitor expoe metricas para Prometheus.

O workflow `Execution Service CI` executa `./mvnw -B verify` em pull requests e pushes para `main`, publica a imagem no GHCR apos um push validado a `main` e pode fazer deploy automaticamente. Para habilitar o deploy, configure a variavel de repositorio `KUBERNETES_DEPLOY_ENABLED=true` e os secrets `KUBE_CONFIG_B64` (kubeconfig em Base64), `GHCR_USERNAME` e `GHCR_READ_TOKEN`. A protecao de `main` deve exigir a checagem `verify`; a imagem precisa estar disponivel no GHCR para o cluster.
