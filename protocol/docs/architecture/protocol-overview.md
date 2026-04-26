# Protocol Overview

## Purpose
Define the official communication model used between XCore components.

## Principles
- protocol-first
- language-neutral
- strict canonical contracts
- compatibility through explicit adapters
- stable route metadata
- generated consumption surfaces from canonical definitions

## Message Families
- moderation
- discord integration
- maps RPC
- chat and heartbeat

## Boundary
Application repositories consume this protocol. They do not independently redefine canonical wire contracts.

## Consumption Model
The authored source of truth is the protocol spec layer:
- schemas
- shared subtypes
- envelopes
- route manifests

Those definitions should feed generated Java/Python DTO and model artifacts. Consumer repositories keep only thin adapters between internal models and generated protocol artifacts.
