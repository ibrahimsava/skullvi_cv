/**
 * Mandataire du serveur de développement.
 *
 * Pourquoi ce fichier existe
 * --------------------------
 * En production, nginx sert le front et l'API sur la même origine, et
 * `environment.prod.ts` utilise l'URL relative `/api/v1` : il n'y a donc aucun CORS.
 *
 * En local sans mandataire, le front appelait `http://localhost:8080` depuis
 * `http://localhost:<port>`, soit une autre origine. Le navigateur exigeait alors un
 * en-tête CORS — et comme le port du serveur de développement peut changer (une variable
 * d'environnement `PORT` le force ailleurs), il n'était jamais dans la liste autorisée
 * côté serveur. D'où le blocage.
 *
 * Avec ce mandataire, le navigateur n'appelle que son propre serveur, qui relaie vers le
 * backend. Même origine, donc plus de CORS du tout en local. Et la production reste
 * inchangée, puisqu'elle utilisait déjà l'URL relative.
 *
 * Usage
 * -----
 *   ng serve                                            → backend sur localhost:8080
 *   BACKEND_URL=http://localhost:18080 ng serve          → autre port
 *   BACKEND_URL=http://192.168.1.20:8080 ng serve        → backend sur une autre machine
 *
 * Fichier en CommonJS (.cjs) et non JSON, uniquement pour pouvoir lire la variable
 * d'environnement.
 */
const target = process.env.BACKEND_URL || 'http://localhost:8080';

console.log(`[proxy] /api  →  ${target}`);

module.exports = {
  '/api': {
    target,
    secure: false,
    changeOrigin: true,
    logLevel: 'info',
  },
};
