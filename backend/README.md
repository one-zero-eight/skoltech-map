# Skoltech Map

## Table of contents

Did you know that GitHub supports table of
contents [by default](https://github.blog/changelog/2021-04-13-table-of-contents-support-in-markdown-files/) 🤔

## About

This is the API for skoltech campus map application.

### Features

- Collect measurements from devices to build a map


### Technologies

- [Python 3.12](https://www.python.org/downloads/) & [uv](https://github.com/astral-sh/uv)
- [FastAPI](https://fastapi.tiangolo.com/) & [Pydantic](https://docs.pydantic.dev/latest/)
- Formatting and linting: [Ruff](https://docs.astral.sh/ruff/), [pre-commit](https://pre-commit.com/)
- Deployment: [Docker](https://www.docker.com/), [Docker Compose](https://docs.docker.com/compose/),
  [GitHub Actions](https://github.com/features/actions)

## Development

### Set up for development

1. Install [Python 3.12+](https://www.python.org/downloads/), Install [uv](https://github.com/astral-sh/uv)
2. Install project dependencies with `uv`:
   ```bash
   uv venv
   uv sync
   ```
3. Start development server:
   ```bash
   uv run python -m src --reload
   ```
   > Follow provided instructions if needed
4. Open in the browser: http://localhost:8000
   > The API will be reloaded when you edit the code

> [!IMPORTANT]
> For endpoints requiring authorization, login through /telegram-widget.html,
> don't forget to run your app on **port 80** for that

> [!TIP]
> Edit `settings.yaml` according to your needs, you can view schema in
> [config_schema.py](src/config_schema.py) and in [settings.schema.yaml](settings.schema.yaml)

**Set up PyCharm integrations**

1. Run configurations ([docs](https://www.jetbrains.com/help/pycharm/run-debug-configuration.html#createExplicitly)).
   Right-click the `__main__.py` file in the project explorer, select `Run '__main__'` from the context menu.
2. Ruff ([plugin](https://plugins.jetbrains.com/plugin/20574-ruff)).
   It will lint and format your code. Make sure to enable `Use ruff format` option in plugin settings.
3. Pydantic ([plugin](https://plugins.jetbrains.com/plugin/12861-pydantic)). It will fix PyCharm issues with
   type-hinting.
4. Conventional commits ([plugin](https://plugins.jetbrains.com/plugin/13389-conventional-commit)). It will help you
   to write [conventional commits](https://www.conventionalcommits.org/en/v1.0.0/).

### Deployment

We use Docker with Docker Compose plugin to run the service on servers.

1. Copy the file with settings: `cp settings.example.yaml settings.yaml`
2. Change settings in the `settings.yaml` file according to your needs
   (check [settings.schema.yaml](settings.schema.yaml) for more info)
3. Install Docker with Docker Compose
4. Build a Docker image: `docker compose build --pull`
5. Run the container: `docker compose up --detach`
6. Check the logs: `docker compose logs -f`

# How to update dependencies

## Project dependencies

1. Run `uv add <package>` to add a new dependency
2. Run `uv sync -U` to update all dependencies
3. Run `uv pip list --outdated` to check for outdated dependencies

## Pre-commit hooks

1. Run `uvx pre-commit autoupdate`

