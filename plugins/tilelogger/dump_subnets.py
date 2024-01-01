from pathlib import Path
import re

def f(d, i):
    d[ip[i]] = d.get(ip[i], {})
    return d[ip[i]]

ips = {}
regex = re.compile(r'.*?Kicking connection (\d+\.\d+\.\d+\.\d+) .*(?:\[accent\]Вы были недавно кикнуты с этого сервера.|Reason: Maybe you are a bot. If not, try to reconnect.)')
for file in Path(".").rglob("log-*.txt"):
    for line in open(file, 'r', encoding='utf-8'):
        smatch = regex.match(line)
        if smatch:
            ip = smatch.group(1).split('.')
            d = ips
            d = f(d, 0)
            d = f(d, 1)
            d = f(d, 2)
            d[ip[3]] = d.get(ip[3], 0) + 1

for k8,v in ips.items():
    for k16,v in v.items():
        for k24,v in v.items():
            for k32,a in v.copy().items():
                if a < 10:
                    v.pop(k32)

s8 = ''
s16 = ''
s24 = ''
s32 = ''
for k8,v in ips.items():
    if len(v) >= 128:
        s8 += f'{k8}.0.0.0/8\n'
    else:
        for k16,v in v.items():
            if len(v) >= 64:
                s16 += f'{k8}.{k16}.0.0/16\n'
            else:
                for k24,v in v.items():
                    if len(v) >= 2:
                        s24 += f'{k8}.{k16}.{k24}.0/24\n'
                    else:
                        for k32,a in v.items():
                            s32 += f'{k8}.{k16}.{k24}.{k32}\n'

print(f'{s8}\n{s16}\n{s24}\n{s32}')