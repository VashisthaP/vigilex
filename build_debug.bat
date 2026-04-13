@echo off
cd /d C:\Users\v-vashisthap\vigilex
call gradlew.bat assembleDebug > C:\Users\v-vashisthap\vigilex\build_output.txt 2>&1
echo EXIT_CODE=%ERRORLEVEL% >> C:\Users\v-vashisthap\vigilex\build_output.txt
