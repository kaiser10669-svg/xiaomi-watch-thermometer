#!/bin/bash
set -e

echo "==================================================================="
echo "    Термометр для Xiaomi Watch 5 (Wear OS) - Установка"
echo "==================================================================="
echo ""

ADB="adb"
if ! command -v adb &> /dev/null; then
    echo "[ВНИМАНИЕ] ADB не найден в PATH. Установите android-tools / platform-tools."
    exit 1
fi

echo "Подготовка к установке:"
echo " 1. На часах: Настройки -> Об устройстве -> 7 раз нажмите 'Номер сборки'."
echo " 2. Настройки -> Для разработчиков -> Включите 'Отладка по ADB' и 'Отладка по Wi-Fi'."
echo " 3. Посмотрите IP:порт на часах (например, 192.168.1.45:5555)."
echo ""

read -p "Введите IP:порт часов (или нажмите Enter, если уже подключены): " WATCH_IP

if [ -n "$WATCH_IP" ]; then
    echo "Подключение к $WATCH_IP..."
    adb connect "$WATCH_IP"
    ADB_CMD="adb -s $WATCH_IP"
else
    ADB_CMD="adb"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK_PATH="$SCRIPT_DIR/WatchThermometer.apk"

echo "Установка $APK_PATH..."
$ADB_CMD install -r -d -g "$APK_PATH"

echo ""
echo "==================================================================="
echo "  [УСПЕХ] Термометр успешно установлен на часы!"
echo "==================================================================="
