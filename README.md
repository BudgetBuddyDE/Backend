# Backend

## Getting started

> [!NOTE]  
> This backend is build on **JDK version 17.0.8**

> [!NOTE]  
> You can find the database and docker-compose.yml in the [setup repository](https://github.com/budgetbuddyde/setup).


### Docker

#### Pull or push

```bash
echo <GH_PAT> | docker login ghcr.io -u <GH_USER> --password-stdin
docker pull ghcr.io/budgetbuddyde/backend:latest
# or
docker push ghcr.io/budgetbuddyde/backend:latest
```

#### Build

```bash
docker build . -t ghcr.io/budgetbuddyde/backend:latest
```

#### Run

```bash
docker run -p 80:8080 ghcr.io/budgetbuddyde/backend:latest
```

## Authentification

> [!IMPORTANT]  
> The `Authorization` header should be structured as follows.
>
> `Bearer: UUID.HASHED_PASSWORD`
>
> The values are separated and then verified in the `AuthorizationInterceptor`. The current user for the session is then determined based on the UUID and set as the "user" session attribute.

```mermaid
---
title: Backend Authentification Flow
---
flowchart TD
    401[HTTP 401]
    500[HTTP 500]
    validation_end((End))

    start((Start)) -->|Incoming Request| path_match{URI matches /v1/auth/**}
    path_match -->|Yes| validation_end
    path_match -->|No| has_auth_header{Check if Auth-Header exists \nand provides 'Bearer'}
    has_auth_header -->|No| 401[Set HTTP 401 Unauthorized. Reason: No Bearer-Token we're provided]
    has_auth_header -->|Yes| get_token_bearer[Extract UUID and hashed password from Bearer token]
    get_token_bearer --> validate_bearer_is_UUID{Validate if Bearer is a UUID}
    validate_bearer_is_UUID -->|No, throw IllegalArgumentException| 500[Set HTTP 500 Internal Server Error]
    validate_bearer_is_UUID -->|Yes| retrieve_user[Retrieve User by UUID and password from UserRepository]
    retrieve_user --> is_user_present{Check if User is found}
    is_user_present -->|No| 401[Set HTTP 401 Unauthorized. Reason: Provided Bearer-Token is invalid]
    is_user_present -->|Yes| serialize_user_to_string[Serialize user to String using ObjectMapper]
    serialize_user_to_string -->|On JsonProcessingException| 500[Set HTTP 500 Internal Server Error]
    serialize_user_to_string -->|No Exception| store_to_session[Store serialized user to HTTP Session]
    store_to_session --> validation_end
```