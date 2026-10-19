"""Capture an isolated, already running Minecraft client; never touch mod source."""
from pathlib import Path
import ctypes
from ctypes import wintypes
import json
import shutil
import sys
import time

ROOT = Path(__file__).resolve().parent
SESSION = json.loads((ROOT / 'font-session.json').read_text(encoding='utf-8'))
USER = ctypes.windll.user32
KERNEL = ctypes.windll.kernel32
USER.SetProcessDPIAware()


class Keyboard(ctypes.Structure):
    _fields_ = [('vk', wintypes.WORD), ('scan', wintypes.WORD), ('flags', wintypes.DWORD),
                ('time', wintypes.DWORD), ('extra', ctypes.c_size_t)]


class Mouse(ctypes.Structure):
    _fields_ = [('x', wintypes.LONG), ('y', wintypes.LONG), ('data', wintypes.DWORD),
                ('flags', wintypes.DWORD), ('time', wintypes.DWORD), ('extra', ctypes.c_size_t)]


class Payload(ctypes.Union):
    _fields_ = [('ki', Keyboard), ('mi', Mouse)]


class Input(ctypes.Structure):
    _fields_ = [('type', wintypes.DWORD), ('payload', Payload)]


def focus():
    targets = []
    @ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
    def visit(handle, _):
        pid = wintypes.DWORD()
        USER.GetWindowThreadProcessId(handle, ctypes.byref(pid))
        if pid.value == SESSION['pid'] and USER.IsWindowVisible(handle):
            targets.append(handle)
        return True
    USER.EnumWindows(visit, 0)
    assert targets, 'The isolated game window is absent'
    handle = targets[0]
    foreground_thread = USER.GetWindowThreadProcessId(USER.GetForegroundWindow(), None)
    current_thread = KERNEL.GetCurrentThreadId()
    USER.AttachThreadInput(current_thread, foreground_thread, True)
    USER.ShowWindow(handle, 9)
    USER.BringWindowToTop(handle)
    USER.SetForegroundWindow(handle)
    USER.AttachThreadInput(current_thread, foreground_thread, False)
    time.sleep(.1)
    owned()


def owned():
    pid = wintypes.DWORD()
    USER.GetWindowThreadProcessId(USER.GetForegroundWindow(), ctypes.byref(pid))
    assert pid.value == SESSION['pid'], 'Focus changed; stop without sending input elsewhere'


def tap(key):
    owned()
    scan = USER.MapVirtualKeyW(key, 0)
    USER.keybd_event(key, scan, 0, 0)
    time.sleep(.045)
    USER.keybd_event(key, scan, 2, 0)


def type_text(value):
    for char in value:
        owned()
        events = (Input * 2)(
            Input(1, Payload(ki=Keyboard(0, ord(char), 4, 0, 0))),
            Input(1, Payload(ki=Keyboard(0, ord(char), 6, 0, 0))))
        assert USER.SendInput(2, events, ctypes.sizeof(Input)) == 2
        time.sleep(.004)


def chat(value):
    tap(ord('T'))
    time.sleep(.25)
    type_text(value)
    tap(13)
    time.sleep(.3)


def capture(name):
    focus()
    folder = Path(SESSION['gameDirectory']) / 'screenshots'
    before = set(folder.glob('*.png'))
    tap(0x71)
    for _ in range(30):
        time.sleep(.1)
        new = set(folder.glob('*.png')) - before
        if new:
            time.sleep(.2)
            destination = ROOT / 'font-proof' / name
            destination.parent.mkdir(exist_ok=True)
            shutil.copy2(max(new, key=lambda p: p.stat().st_mtime), destination)
            print(destination.name)
            return
    raise RuntimeError('Minecraft did not save an F2 screenshot')


if __name__ == '__main__':
    focus()
    if '--escape' in sys.argv:
        tap(27)
        time.sleep(.3)
    if '--raw' in sys.argv:
        capture(sys.argv[1])
    elif '--keys' in sys.argv:
        for value in sys.argv[sys.argv.index('--keys') + 1].split(','):
            tap(int(value, 0))
            time.sleep(.2)
    elif '--click' in sys.argv:
        coordinates = sys.argv[sys.argv.index('--click') + 1].split(',')
        point = wintypes.POINT(*map(int, coordinates))
        USER.ClientToScreen(USER.GetForegroundWindow(), ctypes.byref(point))
        USER.SetCursorPos(point.x, point.y)
        USER.mouse_event(2, 0, 0, 0, 0)
        time.sleep(.05)
        USER.mouse_event(4, 0, 0, 0, 0)
    elif '--command' in sys.argv:
        chat(sys.argv[sys.argv.index('--command') + 1])
    else:
        chat('і ї є ґ І Ї Є Ґ  |  Їжак, ґанок, Єдність')
        tap(ord('T'))
        time.sleep(.25)
        type_text('і ї є ґ І Ї Є Ґ')
        time.sleep(.3)
        capture(sys.argv[1])
