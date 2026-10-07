# FrontSkulvi

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 22.2.1.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Vitest](https://vitest.dev/) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

For end-to-end (e2e) testing, run:

```bash
ng e2e
```

Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## JWT Interceptor

Le projet utilise un intercepteur HTTP pour gérer les requêtes authentifiées.

### Rôle de l'intercepteur

- lire le token JWT stocké dans le `localStorage`
- ajouter le header `Authorization: Bearer <token>` sur les requêtes protégées
- laisser passer les endpoints publics sans token
- rediriger vers `/auth` si une route protégée est appelée sans token
- supprimer le token et rediriger si le backend retourne `401`

### Enregistrement global

L'intercepteur est enregistré dans [src/app/app.config.ts](src/app/app.config.ts) avec `provideHttpClient(withInterceptors([authInterceptor]))`.

Cela signifie que toutes les requêtes HTTP de l'application passent par le même mécanisme de sécurité sans devoir le répéter dans chaque composant.

### Fichier principal

Le code principal est dans [src/app/features/inter/inter.ts](src/app/features/inter/inter.ts).

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
