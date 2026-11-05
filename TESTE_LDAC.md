# OpenQCY LDAC Teste — 1.0.1

Base: HttpKiwi/OpenQCY, commit 4eab9deec3a139f9a68ba0277b3e7f38a6ef665e.
Licença MIT preservada em LICENSE. Uso de protocolo documentado publicamente;
nenhum código do aplicativo proprietário QCY foi incorporado.

## O que esta versão faz

- Envia o comando QCY documentado `FF 03 23 01 01` para habilitar LDAC
  (`FF 03 23 01 02` para desabilitar).
- Prepara a reconexão antes da escrita, para não perder um reinício rápido.
- Consulta `FF 03 FE 01 23` quando o fone permanece conectado.
- Aguarda confirmação do fone em vez de considerar a escrita GATT um sucesso.
- Distingue ausência de resposta, estado desativado e resposta desconhecida.
- Registra comando, resposta, firmware e eventos de conexão em diagnóstico local.
- Abre os ajustes Bluetooth do Android para conferir o codec de reprodução.
- Usa um pacote separado, `com.httpkiwi.melocontrol.ldaclab`.

## Como testar no HT05

1. Compile o APK de teste conforme a seção Compilação e instale. Ele pode coexistir com o OpenQCY original.
2. Feche o QCY e o OpenQCY original para evitar comandos concorrentes.
3. Conecte o HT05 ao celular e abra o OpenQCY LDAC Teste.
4. Ative LDAC e aguarde o resultado. Se o fone reiniciar, aguarde a reconexão.
5. Caso o fone confirme, confira o codec nas opções de desenvolvedor do Android
   com música tocando. Pode ser necessário reconectar o áudio Bluetooth.
6. Se falhar, abra “Diagnóstico LDAC”, copie o texto e envie para análise.

## Limites

Esta é uma correção experimental do fluxo do aplicativo. A compatibilidade
do comando com o HT05 e a transmissão LDAC real ainda precisam de teste físico.
Um comando confirmado pelo fone indica a preferência informada pelo firmware;
não demonstra que o Android selecionou LDAC nem que transmitiu a 990 kbps.
Esta versão não altera firmware, não usa root, não injeta codecs e não envia
comandos aleatórios. Ausência de resposta não prova ausência de suporte.

## Compilação

Use Flutter 3.44.5 / Dart 3.12.2, JDK 17 e Android SDK compatível com o projeto.

```sh
flutter pub get
flutter analyze
flutter test
flutter build apk --release --target-platform android-arm64
```

O APK de teste usa a assinatura local de desenvolvimento quando não há uma
chave de release configurada; mantenha a mesma chave para atualizações futuras.

## Validação realizada nesta entrega

- Oito cenários simulados de protocolo passaram em um executável Dart AOT,
  compilado a partir de `tool/ldac_smoke_test.dart`.
- `dart analyze` não encontrou problemas em `lib/core/qcy/ldac_change.dart`.
- As alterações passaram em `git diff --check`.
- O projeto Flutter completo e o APK não foram compilados: o acesso a pub.dev
  e ao download do NDK foi recusado pela rede deste ambiente (HTTP 403).
  Também houve falha do runtime Dart JIT local; a validação isolada usou AOT.
- Não foi executada a suíte Flutter nem feito teste físico no HT05.

## APK pelo GitHub Actions

No fork Matheus-TestUser1/OpenQCY, cada push em `main` executa análise e testes,
compila o APK ARM64 e publica uma pre-release com APK, SHA-256 e estas instruções.
Também é possível iniciar manualmente em Actions → Build and release LDAC APK
→ Run workflow. Em forks novos, pode ser necessário habilitar Actions na aba Actions.

O pacote continua sendo `com.httpkiwi.melocontrol.ldaclab`. O CI conserva a chave
de desenvolvimento no cache para permitir atualizações entre builds. Se o cache
for removido, a chave pode mudar; para uma assinatura permanente, configure os
secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` e
`ANDROID_STORE_PASSWORD`. O CI usará essa assinatura automaticamente.

O registro de validação acima descreve a preparação local inicial. O resultado
da análise, dos testes Flutter e da compilação completos fica no run de Actions
referenciado em cada release.
