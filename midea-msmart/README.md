This is a library to allow communicating to a Midea AC via the Local area network.

# midea-msmart

[![Build Status](https://travis-ci.org/mac-zhou/midea-msmart.svg?branch=master)](https://travis-ci.org/mac-zhou/midea-msmart)
[![PyPI](https://img.shields.io/pypi/v/msmart.svg?maxAge=3600)](https://pypi.org/project/msmart/)

This a mirror from the repo at [NeoAcheron/midea-ac-py](https://github.com/NeoAcheron/midea-ac-py).

But this library just allow communicating to a Midea AC via the Local area network, not via the Midea Cloud yet.

Thanks for [yitsushi's project](https://github.com/yitsushi/midea-air-condition), [NeoAcheron's project](https://github.com/NeoAcheron/midea-ac-py), [andersonshatch's project](https://github.com/andersonshatch/midea-ac-py).

## How to Use

- you can use command ```midea-discover``` to discover midea devices on the host in the same Local area network. Note: This component only supports devices with model 0xac (air conditioner) and words ```supported``` in the output.

    ```shell
    pip3 install msmart
    midea-discover
    ```

- then you can use a custom component for Home Assistant [here](https://github.com/mac-zhou/midea-ac-py)

```
midea-msmart
├─ .bumpversion.cfg
├─ .git
│  ├─ config
│  ├─ description
│  ├─ HEAD
│  ├─ hooks
│  │  ├─ applypatch-msg.sample
│  │  ├─ commit-msg.sample
│  │  ├─ fsmonitor-watchman.sample
│  │  ├─ post-update.sample
│  │  ├─ pre-applypatch.sample
│  │  ├─ pre-commit.sample
│  │  ├─ pre-merge-commit.sample
│  │  ├─ pre-push.sample
│  │  ├─ pre-rebase.sample
│  │  ├─ pre-receive.sample
│  │  ├─ prepare-commit-msg.sample
│  │  ├─ push-to-checkout.sample
│  │  ├─ sendemail-validate.sample
│  │  └─ update.sample
│  ├─ index
│  ├─ info
│  │  └─ exclude
│  ├─ logs
│  │  ├─ HEAD
│  │  └─ refs
│  │     ├─ heads
│  │     │  └─ master
│  │     └─ remotes
│  │        └─ origin
│  │           └─ HEAD
│  ├─ objects
│  │  ├─ info
│  │  └─ pack
│  │     ├─ pack-e531933ad43e79e48d9a524c7c486ad02296a910.idx
│  │     ├─ pack-e531933ad43e79e48d9a524c7c486ad02296a910.pack
│  │     └─ pack-e531933ad43e79e48d9a524c7c486ad02296a910.rev
│  ├─ packed-refs
│  └─ refs
│     ├─ heads
│     │  └─ master
│     ├─ remotes
│     │  └─ origin
│     │     └─ HEAD
│     └─ tags
├─ .gitignore
├─ .travis
│  └─ push.sh
├─ .travis.yml
├─ 0xAC.zip
├─ example.py
├─ LICENSE
├─ msmart
│  ├─ base_command.py
│  ├─ cli.py
│  ├─ client.py
│  ├─ cloud.py
│  ├─ const.py
│  ├─ crc8.py
│  ├─ device
│  │  ├─ AC
│  │  │  ├─ appliance.py
│  │  │  ├─ command.py
│  │  │  └─ __init__.py
│  │  ├─ base.py
│  │  ├─ DB
│  │  │  ├─ appliance.py
│  │  │  ├─ command.py
│  │  │  └─ __init__.py
│  │  └─ __init__.py
│  ├─ lan.py
│  ├─ packet_builder.py
│  ├─ scanner.py
│  ├─ security.py
│  ├─ utils.py
│  └─ __init__.py
├─ README.md
├─ requirements.txt
└─ setup.py

```