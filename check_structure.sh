#!/bin/bash

echo "=== Checking BELSI.Work Project Structure ==="
echo ""

# Check key directories
echo "Checking directories..."
directories=(
    "app/src/main/java/com/belsi/work/data/models"
    "app/src/main/java/com/belsi/work/data/remote/api"
    "app/src/main/java/com/belsi/work/data/local/dao"
    "app/src/main/java/com/belsi/work/data/local/entities"
    "app/src/main/java/com/belsi/work/data/repositories"
    "app/src/main/java/com/belsi/work/domain/usecases/auth"
    "app/src/main/java/com/belsi/work/presentation/theme"
    "app/src/main/java/com/belsi/work/presentation/navigation"
    "app/src/main/java/com/belsi/work/presentation/screens/auth/phone"
    "app/src/main/java/com/belsi/work/di"
)

for dir in "${directories[@]}"; do
    if [ -d "$dir" ]; then
        echo "✓ $dir"
    else
        echo "✗ $dir"
    fi
done

echo ""
echo "Checking key files..."

files=(
    "app/build.gradle.kts"
    "app/src/main/AndroidManifest.xml"
    "app/src/main/java/com/belsi/work/BelsiWorkApp.kt"
    "app/src/main/java/com/belsi/work/MainActivity.kt"
    "app/src/main/java/com/belsi/work/data/models/User.kt"
    "app/src/main/java/com/belsi/work/data/local/PrefsManager.kt"
    "app/src/main/java/com/belsi/work/data/local/AppDatabase.kt"
    "app/src/main/java/com/belsi/work/presentation/theme/Theme.kt"
    "app/src/main/java/com/belsi/work/presentation/navigation/AppRoute.kt"
    "app/src/main/res/values/strings.xml"
)

for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        echo "✓ $file"
    else
        echo "✗ $file"
    fi
done

echo ""
echo "=== Structure Check Complete ==="
