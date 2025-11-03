# Flutter Coletor RFID

> Flutter app para leitura de tags RFID usando coletores TSL/ACURA via Bluetooth

- Leitura contínua de tags
- Suporte ao gatilho físico do coletor

## 🛠️ Tecnologias

- Flutter 3.x
- Kotlin (Android Native)
- Rfid.AsciiProtocol SDK 3.5.0 (ACURA)

## 📋 Hardware Testado

- TSL 1128
- ACURA BTL-1000

**⚠️ Importante:** O SDK é proprietário da ACURA/TSL. 
Consulte os termos de licença antes de usar.

1. Baixe o SDK oficial: [ACURA Android SDK](https://drive.google.com/file/d/13jLHJAMarf2aqClMyDMMkCuw6NU6M76Q/view?usp=drive_link)
2. Extraia o arquivo `Rfid.AsciiProtocol-3.5.0-release.aar` em `Rfid.AsciiProtocol-Library/`
3. Coloque em `android/app/libs/`
