#!/bin/bash

# Остановить скрипт, если что-то пошло не так
set -e

echo "--- Запуск настройки окружения для агента на Arch Linux ---"

# 1. Обновление баз данных пакетов
echo "Обновление баз данных..."
sudo pacman -Sy

# 2. Функция для проверки и установки пакетов pacman
install_pkg() {
    if ! command -v "$1" &> /dev/null; then
        echo "Пакет $1 не найден. Установка..."
        sudo pacman -S --noconfirm "$2"
    else
        echo "Пакет $1 уже установлен."
    fi
}

# 3. Системные зависимости
install_pkg "python" "python"
install_pkg "pip" "python-pip"
install_pkg "node" "nodejs"
install_pkg "npm" "npm"
install_pkg "scrot" "scrot"
install_pkg "git" "git"
install_pkg "gh" "github-cli"

# 4. Настройка структуры папок
echo "Настройка структуры папок..."
# Предполагается, что скрипт запускается из папки проекта, где лежит этот скрипт
cd ..
mkdir -p agent_workspace

# 5. Создание виртуального окружения Python
if [ ! -d "agent_workspace/.venv" ]; then
    echo "Создание виртуального окружения..."
    python -m venv agent_workspace/.venv
else
    echo "Виртуальное окружение уже существует."
fi

# 6. Установка Python-библиотек
echo "Установка Python-библиотек..."
agent_workspace/.venv/bin/pip install --upgrade pip
agent_workspace/.venv/bin/pip install pyautogui pillow pygetwindow

# 7. Установка ядра агента (Node.js)
echo "Проверка установки ядра агента (pi-coding-agent)..."
if ! command -v pi-coding-agent &> /dev/null; then
    echo "Установка агента через npm..."
    sudo npm install -g @earendil-works/pi-coding-agent
else
    echo "Агент уже установлен."
fi

echo "--- Настройка завершена успешно! ---"
