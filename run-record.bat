@echo off
echo ========================================
echo FastWakeWord Template Recorder
echo ========================================

call mvn -q exec:exec -Darg0="--record"

pause
