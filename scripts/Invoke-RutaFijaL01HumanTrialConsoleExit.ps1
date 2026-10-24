[CmdletBinding()]
param([Parameter(Mandatory)][int]$TrialProcessId, [switch]$Execute)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# Test-only bridge to the existing public `salir` command. Never reads console history.
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;
public static class L01PublicConsoleExit {
 [StructLayout(LayoutKind.Sequential)] struct COORD { public short X,Y; }
 [StructLayout(LayoutKind.Sequential)] struct RECT { public short L,T,R,B; }
 [StructLayout(LayoutKind.Sequential)] struct INFO { public COORD Size,Cursor; public short Attr; public RECT Window; public COORD Max; }
 [StructLayout(LayoutKind.Explicit,Size=20)] struct INPUT {
  [FieldOffset(0)] public short Type;
  [FieldOffset(4)] public int Down;
  [FieldOffset(8)] public short Repeat;
  [FieldOffset(10)] public short VK;
  [FieldOffset(14)] public char Character;
 }
 [DllImport("kernel32.dll")] static extern bool FreeConsole();
 [DllImport("kernel32.dll",SetLastError=true)] static extern bool AttachConsole(uint process);
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern IntPtr CreateFile(string name,uint access,uint share,IntPtr security,uint creation,uint flags,IntPtr template);
 [DllImport("kernel32.dll")] static extern bool GetConsoleScreenBufferInfo(IntPtr output,out INFO info);
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode)] static extern bool ReadConsoleOutputCharacter(IntPtr output,StringBuilder buffer,uint size,COORD origin,out uint count);
 [DllImport("kernel32.dll",CharSet=CharSet.Unicode)] static extern bool WriteConsoleInput(IntPtr input,INPUT[] records,uint size,out uint written);
 [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
 public static string Run(uint process,bool execute) {
  FreeConsole(); if(!AttachConsole(process)) return "STOP_CONSOLE_ATTACH_UNAVAILABLE";
  IntPtr output=IntPtr.Zero,input=IntPtr.Zero;
  try {
   output=CreateFile("CONOUT$",0x80000000,3,IntPtr.Zero,3,0,IntPtr.Zero);
   INFO info; if(!GetConsoleScreenBufferInfo(output,out info)) return "STOP_CONSOLE_POSITION_UNAVAILABLE";
   if(info.Cursor.X<1 || info.Cursor.X>80) return "STOP_NOT_AT_PUBLIC_PROMPT_CURSOR_"+info.Cursor.X;
   var row=new StringBuilder(80); uint read;
   if(!ReadConsoleOutputCharacter(output,row,(uint)info.Cursor.X,new COORD{X=0,Y=info.Cursor.Y},out read)) return "STOP_PROMPT_UNAVAILABLE";
   string visible=row.ToString();
   if(read!=12 || !visible.StartsWith("Ensayo L01> ",StringComparison.Ordinal)) return "STOP_NOT_AT_PUBLIC_PROMPT_LENGTH_"+visible.Length+"_INDEX_"+visible.IndexOf("Ensayo L01>")+"_READ_"+read;
   if(!execute) return "READY_EXISTING_PUBLIC_SALIR_ONLY";
   input=CreateFile("CONIN$",0xC0000000,3,IntPtr.Zero,3,0,IntPtr.Zero);
   var keys=new INPUT[12]; string command="salir\r";
   for(int i=0;i<command.Length;i++) {
    keys[2*i]=new INPUT{Type=1,Down=1,Repeat=1,Character=command[i],VK=(short)(command[i]=='\r'?13:0)};
    keys[2*i+1]=new INPUT{Type=1,Down=0,Repeat=1,Character=command[i],VK=(short)(command[i]=='\r'?13:0)};
   }
   uint written; return WriteConsoleInput(input,keys,(uint)keys.Length,out written)&&written==keys.Length ? "PUBLIC_SALIR_SUBMITTED" : "STOP_PUBLIC_SALIR_NOT_SUBMITTED";
  } finally { if(output!=IntPtr.Zero) CloseHandle(output); if(input!=IntPtr.Zero) CloseHandle(input); FreeConsole(); }
 }
}
'@
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskMarker = Join-Path $taskRoot 'backend/.local/l01-human-trial.active'
if (([IO.File]::ReadAllText($taskMarker).Trim()) -ne '140c10a1-be0f-4d08-a6d4-c796e2ed107a') { throw 'Caso distinto; detener.' }
$taskListener = @(Get-NetTCPConnection -State Listen -LocalPort 4311,8091 -ErrorAction Stop)
if ($taskListener.Count -ne 2 -or @($taskListener | Where-Object { $_.OwningProcess -ne $TrialProcessId -or $_.LocalAddress -ne '127.0.0.1' }).Count -ne 0) { throw 'Proceso/puertos distintos; detener.' }
if ((Get-Process -Id $TrialProcessId -ErrorAction Stop).ProcessName -ne 'java') { throw 'Proceso distinto; detener.' }
$taskResult = [L01PublicConsoleExit]::Run([uint32]$TrialProcessId, $Execute.IsPresent)
[IO.File]::WriteAllText((Join-Path $taskRoot 'backend/target/l01-public-console-exit-result.json'), (@{ result=$taskResult; execute=$Execute.IsPresent; processId=$TrialProcessId; observedAtUtc=[DateTimeOffset]::UtcNow.ToString('o') } | ConvertTo-Json))
Write-Output ('L01_EXISTING_HARNESS_EXIT=' + $taskResult)
if ($taskResult.StartsWith('STOP_')) { exit 1 }
