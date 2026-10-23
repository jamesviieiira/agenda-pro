# Studio Celine — Agenda (Android)

App de agenda para profissionais autônomos (beleza, saúde, estética, personal etc.), feito em Kotlin + Jetpack Compose + Room.
Funciona 100% offline; os dados ficam no aparelho.

## Novidades da v0.3
- Visual do Studio Celine: preto e champagne, logo extraída da foto da parede do studio e ícone novo
- Nova tela **Início**: resumo do dia, próximo atendimento, atalhos (novo agendamento, novo cliente, horários livres, lembretes), alertas e resultado do mês
- **Mais** reorganizado em blocos com ícones: Atendimento, Clientes, Negócio e Ajustes
- **Serviços sugeridos** de lash, sobrancelha, unhas e massagem (só ajustar os preços)
- **Dados do studio** (nome e WhatsApp) usados nas mensagens e nos horários livres
- Expediente padrão: segunda a sábado, 08:00 às 19:00
- Chave de assinatura fixa (`app/debug.keystore`): a partir da v0.3.1 os APKs novos instalam POR CIMA do anterior, sem desinstalar. A v0.2 foi assinada com outra chave, então a troca da v0.2 para a v0.3.1 exige desinstalar uma única vez (faça o Backup antes)
- O banco de dados não mudou da v0.2 para a v0.3.1: restaurando o backup, os dados voltam

## Funções (v0.2)
- **Agenda**: visão diária com faixa semanal, filtro por colaborador, agendar/remarcar/cancelar/excluir,
  aviso de conflito de horário, de feriado e de fora do expediente
- **Horários livres**: gera a lista de vagas dos próximos dias para enviar aos clientes (alternativa offline ao agendamento online)
- **WhatsApp**: confirmação, lembrete e aniversário com textos editáveis ({cliente} {servico} {data} {hora})
- **Clientes**: cadastro, busca, aniversário, observação, importação da agenda de contatos do celular
- **Catálogo**: serviços e produtos (venda, custo, comissão); produto pode ser vendido dentro do agendamento
- **Pacotes de sessões**: modelos, venda para cliente, uso de sessão no agendamento (ou manual), estorno ao cancelar
- **Cartão fidelidade**: selos por atendimento concluído, prêmio configurável, resgate
- **Equipe**: colaboradores com % de comissão (serviços e produtos)
- **Expediente e feriados**: turnos por dia da semana, feriados nacionais (inclui Carnaval, Sexta Santa, Corpus Christi) e dias bloqueados
- **Financeiro**: receita, a receber, taxas de cartão (débito/crédito), comissões, custo de produtos, despesas (fixas mensais), lucro líquido, meta, por serviço e forma de pagamento
- **Lembrete diário**: notificação com os atendimentos do dia seguinte (horário configurável) + tela "Lembretes de amanhã" com envio por WhatsApp
- **Relatórios**: ticket médio, taxa de cancelamento, melhores clientes, serviços mais feitos, dias e horários mais cheios, comissões, margem de produtos; compartilhável como texto
- **Backup**: exporta/restaura um arquivo JSON; salve numa pasta do Google Drive/Dropbox para ter cópia na nuvem

## Como compilar no GitHub
1. Crie um repositório e envie estes arquivos na branch `main`.
2. Aba **Actions** → workflow **Build APK** (roda a cada push; também em "Run workflow").
3. Ao terminar, baixe o artefato **agenda-pro-debug-apk** e instale o `app-debug.apk`.

## Notas
- A v0.2 muda o banco de dados (versão 2) e **apaga os dados da v0.1** na atualização. Use Backup antes de futuras versões.
- Agendamento online com link público e sincronização automática na nuvem exigem um servidor (ex.: Firebase) e não estão incluídos.
