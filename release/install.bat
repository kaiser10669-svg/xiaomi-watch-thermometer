@echo off
chcp 65001 > nul
title Установка Xiaomi Watch Thermometer
echo ===================================================================
echo     Термометр для Xiaomi Watch 5 (и других часов Wear OS)
echo ===================================================================
echo.

set "ADB=adb"
where adb >nul 2>&1
if %errorlevel% neq 0 (
    if exist "%~dp0adb.exe" (
        set "ADB=%~dp0adb.exe"
    ) else if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
        set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    ) else (
        echo [ВНИМАНИЕ] ADB не найден в системе.
        echo Скачайте Android SDK Platform-Tools или установите ADB в PATH.
        echo.
    )
)

echo Подготовка к установке:
echo  1. На часах откройте: Настройки -> Об устройстве -> 7 раз нажмите на "Номер сборки".
echo  2. Перейдите в: Настройки -> Для разработчиков.
echo  3. Включите "Отладка по ADB" и "Отладка по беспроводной сети (Wi-Fi)".
echo  4. Посмотрите IP-адрес и порт на экране часов (например, 192.168.1.45:5555).
echo.
set /p WATCH_IP="Введите IP:порт часов (или нажмите Enter, если уже подключены через USB/ADB): "

if not "%WATCH_IP%"=="" (
    echo.
    echo Подключение к часам %WATCH_IP%...
    "%ADB%" connect %WATCH_IP%
    set "ADB_CMD="%ADB%" -s %WATCH_IP%"
) else (
    set "ADB_CMD="%ADB%""
)

echo.
echo Установка WatchThermometer.apk на часы...
%ADB_CMD% install -r -d -g "%~dp0WatchThermometer.apk"
if %errorlevel% neq 0 (
    echo.
    echo [ОШИБКА] Не удалось установить APK на часы.
    echo Проверьте, подтвердили ли вы запрос отладки на экране часов (кнопка 'Всегда разрешать').
    pause
    exit /b %errorlevel%
)

echo.
echo ===================================================================
echo   [УСПЕХ] Термометр успешно установлен на ваши часы!
echo   Ищите приложение "Термометр" в списке приложений часов.
echo ===================================================================
pause
