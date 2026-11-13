"""Traducciones de los textos nuevos de la v9 (Google: iniciar sesión vs registrarse, acceso decorado, sonidos,
testimonios con carrusel y formulario). Formato: español → (en, fr, it, pt, de).
Uso: python tools/i18n/v9_texts.py  (añade las que falten a static/i18n/<idioma>.json y quita las que ya no se usan)."""
import json
import sys
from pathlib import Path

T = {
'El acceso con Google estará disponible muy pronto en esta tienda.': ('Sign-in with Google will be available in this store very soon.', 'La connexion avec Google sera bientôt disponible dans cette boutique.', "L'accesso con Google sarà presto disponibile in questo negozio.", 'O acesso com Google estará disponível em breve nesta loja.', 'Die Anmeldung mit Google ist in diesem Shop bald verfügbar.'),
'No encontramos una cuenta POLYMER para esa cuenta de Google': ("We couldn't find a POLYMER account for that Google account", "Nous n'avons trouvé aucun compte POLYMER pour ce compte Google", 'Non abbiamo trovato un account POLYMER per questo account Google', 'Não encontramos uma conta POLYMER para essa conta do Google', 'Wir haben kein POLYMER-Konto für dieses Google-Konto gefunden'),
'Para entrar con Google primero crea tu cuenta: es gratis y toma un minuto. Te enviaremos un código de 6 dígitos a tu correo para confirmarla.': ("To sign in with Google, first create your account: it's free and takes a minute. We'll send a 6-digit code to your email to confirm it.", "Pour vous connecter avec Google, créez d'abord votre compte : c'est gratuit et cela prend une minute. Nous vous enverrons un code à 6 chiffres par e-mail pour le confirmer.", "Per accedere con Google crea prima il tuo account: è gratis e richiede un minuto. Ti invieremo un codice di 6 cifre via e-mail per confermarlo.", 'Para entrar com Google, primeiro crie sua conta: é grátis e leva um minuto. Enviaremos um código de 6 dígitos para o seu e-mail para confirmá-la.', 'Um dich mit Google anzumelden, erstelle zuerst dein Konto: Es ist kostenlos und dauert eine Minute. Wir senden dir einen 6-stelligen Code per E-Mail zur Bestätigung.'),
'Registrarme con Google': ('Sign up with Google', "M'inscrire avec Google", 'Registrami con Google', 'Cadastrar-me com Google', 'Mit Google registrieren'),
'Esa cuenta de Google no está registrada en POLYMER.': ('That Google account is not registered in POLYMER.', "Ce compte Google n'est pas inscrit sur POLYMER.", 'Questo account Google non è registrato su POLYMER.', 'Essa conta do Google não está cadastrada na POLYMER.', 'Dieses Google-Konto ist bei POLYMER nicht registriert.'),
'Código de 6 dígitos y «Recordarme 30 días»': ('6-digit code and “Remember me for 30 days”', 'Code à 6 chiffres et « Se souvenir de moi 30 jours »', 'Codice di 6 cifre e «Ricordami per 30 giorni»', 'Código de 6 dígitos e «Lembrar de mim por 30 dias»', '6-stelliger Code und „30 Tage angemeldet bleiben“'),
'Cliente POLYMER': ('POLYMER customer', 'Client POLYMER', 'Cliente POLYMER', 'Cliente POLYMER', 'POLYMER-Kunde'),
'Crea tu cuenta en un minuto, con tu correo o con Google, y compra tecnología con garantía oficial.': ('Create your account in a minute, with your email or with Google, and buy technology with an official warranty.', 'Créez votre compte en une minute, avec votre e-mail ou avec Google, et achetez de la technologie avec garantie officielle.', 'Crea il tuo account in un minuto, con la tua e-mail o con Google, e acquista tecnologia con garanzia ufficiale.', 'Crie sua conta em um minuto, com seu e-mail ou com Google, e compre tecnologia com garantia oficial.', 'Erstelle dein Konto in einer Minute, mit deiner E-Mail oder mit Google, und kaufe Technik mit offizieller Garantie.'),
'productos': ('products', 'produits', 'prodotti', 'produtos', 'Produkte'),
'categorías': ('categories', 'catégories', 'categorie', 'categorias', 'Kategorien'),
'seguimiento': ('tracking', 'suivi', 'tracciamento', 'rastreamento', 'Sendungsverfolgung'),
'Pulsa Esc o haz clic para continuar': ('Press Esc or click to continue', 'Appuyez sur Échap ou cliquez pour continuer', 'Premi Esc o fai clic per continuare', 'Pressione Esc ou clique para continuar', 'Drücke Esc oder klicke, um fortzufahren'),
'Sonidos: activados': ('Sounds: on', 'Sons : activés', 'Suoni: attivi', 'Sons: ativados', 'Töne: an'),
'Sonidos: silenciados': ('Sounds: muted', 'Sons : coupés', 'Suoni: disattivati', 'Sons: silenciados', 'Töne: stumm'),
'Testimonios': ('Testimonials', 'Témoignages', 'Testimonianze', 'Depoimentos', 'Erfahrungsberichte'),
'{0} de 5 estrellas': ('{0} out of 5 stars', '{0} étoiles sur 5', '{0} stelle su 5', '{0} de 5 estrelas', '{0} von 5 Sternen'),
'Foto de {0}': ('Photo of {0}', 'Photo de {0}', 'Foto di {0}', 'Foto de {0}', 'Foto von {0}'),
'Equipo POLYMER': ('POLYMER team', 'Équipe POLYMER', 'Team POLYMER', 'Equipe POLYMER', 'POLYMER-Team'),
'Perfil de LinkedIn de {0}': ("{0}'s LinkedIn profile", 'Profil LinkedIn de {0}', 'Profilo LinkedIn di {0}', 'Perfil do LinkedIn de {0}', 'LinkedIn-Profil von {0}'),
'Ver perfil en LinkedIn': ('View LinkedIn profile', 'Voir le profil LinkedIn', 'Vedi il profilo LinkedIn', 'Ver perfil no LinkedIn', 'LinkedIn-Profil ansehen'),
'carrusel': ('carousel', 'carrousel', 'carosello', 'carrossel', 'Karussell'),
'Testimonios de POLYMER': ('POLYMER testimonials', 'Témoignages POLYMER', 'Testimonianze POLYMER', 'Depoimentos da POLYMER', 'POLYMER-Erfahrungsberichte'),
'Testimonio {0} de {1}: {2}': ('Testimonial {0} of {1}: {2}', 'Témoignage {0} sur {1} : {2}', 'Testimonianza {0} di {1}: {2}', 'Depoimento {0} de {1}: {2}', 'Erfahrungsbericht {0} von {1}: {2}'),
'Testimonio anterior': ('Previous testimonial', 'Témoignage précédent', 'Testimonianza precedente', 'Depoimento anterior', 'Vorheriger Erfahrungsbericht'),
'Testimonio siguiente': ('Next testimonial', 'Témoignage suivant', 'Testimonianza successiva', 'Próximo depoimento', 'Nächster Erfahrungsbericht'),
'¿Compraste en POLYMER? Cuéntanos tu experiencia': ('Bought at POLYMER? Tell us about your experience', 'Vous avez acheté chez POLYMER ? Racontez-nous votre expérience', 'Hai acquistato su POLYMER? Raccontaci la tua esperienza', 'Comprou na POLYMER? Conte sua experiência', 'Bei POLYMER gekauft? Erzähl uns von deiner Erfahrung'),
'Inicia sesión con tu cuenta de cliente para dejar tu testimonio. Aparecerá en el carrusel al instante.': ('Sign in with your customer account to leave your testimonial. It will appear in the carousel right away.', 'Connectez-vous avec votre compte client pour laisser votre témoignage. Il apparaîtra immédiatement dans le carrousel.', 'Accedi con il tuo account cliente per lasciare la tua testimonianza. Apparirà subito nel carosello.', 'Entre com sua conta de cliente para deixar seu depoimento. Ele aparecerá no carrossel na hora.', 'Melde dich mit deinem Kundenkonto an, um deinen Erfahrungsbericht zu hinterlassen. Er erscheint sofort im Karussell.'),
'Iniciar sesión para opinar': ('Sign in to share your opinion', 'Se connecter pour donner son avis', 'Accedi per lasciare un parere', 'Entrar para opinar', 'Anmelden, um deine Meinung zu teilen'),
'Deja tu testimonio': ('Leave your testimonial', 'Laissez votre témoignage', 'Lascia la tua testimonianza', 'Deixe seu depoimento', 'Hinterlasse deinen Erfahrungsbericht'),
'Los testimonios son de clientes. Inicia sesión con una cuenta de cliente para dejar el tuyo.': ('Testimonials come from customers. Sign in with a customer account to leave yours.', 'Les témoignages viennent des clients. Connectez-vous avec un compte client pour laisser le vôtre.', 'Le testimonianze sono dei clienti. Accedi con un account cliente per lasciare la tua.', 'Os depoimentos são de clientes. Entre com uma conta de cliente para deixar o seu.', 'Erfahrungsberichte stammen von Kunden. Melde dich mit einem Kundenkonto an, um deinen zu hinterlassen.'),
'¿Qué te pareció tu compra, la entrega o la página? (20 a 600 caracteres)': ('What did you think of your purchase, the delivery or the website? (20 to 600 characters)', "Qu'avez-vous pensé de votre achat, de la livraison ou du site ? (20 à 600 caractères)", "Cosa ne pensi del tuo acquisto, della consegna o del sito? (da 20 a 600 caratteri)", 'O que achou da sua compra, da entrega ou do site? (20 a 600 caracteres)', 'Wie fandest du deinen Kauf, die Lieferung oder die Website? (20 bis 600 Zeichen)'),
'Ej.: Estudiante de ingeniería, diseñadora, gamer': ('E.g.: Engineering student, designer, gamer', 'Ex. : étudiant en ingénierie, designer, gamer', 'Es.: studente di ingegneria, designer, gamer', 'Ex.: estudante de engenharia, designer, gamer', 'Z. B.: Ingenieurstudent, Designerin, Gamer'),
'Publicar testimonio': ('Publish testimonial', 'Publier le témoignage', 'Pubblica la testimonianza', 'Publicar depoimento', 'Erfahrungsbericht veröffentlichen'),
'Publicarás como {0}. Puedes editarlo cuando quieras; solo hay uno por cuenta.': ("You'll post as {0}. You can edit it whenever you like; there is only one per account.", 'Vous publierez en tant que {0}. Vous pouvez le modifier à tout moment ; un seul par compte.', 'Pubblicherai come {0}. Puoi modificarla quando vuoi; ce n’è una sola per account.', 'Você publicará como {0}. Pode editá-lo quando quiser; há apenas um por conta.', 'Du veröffentlichst als {0}. Du kannst ihn jederzeit bearbeiten; pro Konto gibt es nur einen.'),
'Tu calificación': ('Your rating', 'Votre note', 'La tua valutazione', 'Sua avaliação', 'Deine Bewertung'),
'Tu testimonio': ('Your testimonial', 'Votre témoignage', 'La tua testimonianza', 'Seu depoimento', 'Dein Erfahrungsbericht'),
'¿A qué te dedicas? (opcional)': ('What do you do? (optional)', 'Que faites-vous ? (facultatif)', 'Di cosa ti occupi? (facoltativo)', 'O que você faz? (opcional)', 'Was machst du beruflich? (optional)'),
'Tu perfil de LinkedIn (opcional)': ('Your LinkedIn profile (optional)', 'Votre profil LinkedIn (facultatif)', 'Il tuo profilo LinkedIn (facoltativo)', 'Seu perfil do LinkedIn (opcional)', 'Dein LinkedIn-Profil (optional)'),
'Actualizar testimonio': ('Update testimonial', 'Mettre à jour le témoignage', 'Aggiorna la testimonianza', 'Atualizar depoimento', 'Erfahrungsbericht aktualisieren'),
'Cuéntanos tu experiencia en al menos 20 caracteres.': ('Tell us about your experience in at least 20 characters.', 'Racontez votre expérience en au moins 20 caractères.', 'Raccontaci la tua esperienza in almeno 20 caratteri.', 'Conte sua experiência em pelo menos 20 caracteres.', 'Beschreibe deine Erfahrung in mindestens 20 Zeichen.'),
'¡Gracias! Tu testimonio ya está en el carrusel': ('Thank you! Your testimonial is now in the carousel', 'Merci ! Votre témoignage est maintenant dans le carrousel', 'Grazie! La tua testimonianza è già nel carosello', 'Obrigado! Seu depoimento já está no carrossel', 'Danke! Dein Erfahrungsbericht ist jetzt im Karussell'),
'Aún no hay testimonios. ¡Sé el primero!': ('No testimonials yet. Be the first!', "Pas encore de témoignages. Soyez le premier !", 'Ancora nessuna testimonianza. Sii il primo!', 'Ainda não há depoimentos. Seja o primeiro!', 'Noch keine Erfahrungsberichte. Sei der Erste!'),
'Los testimonios no están disponibles en este momento.': ('Testimonials are not available right now.', 'Les témoignages ne sont pas disponibles pour le moment.', 'Le testimonianze non sono disponibili in questo momento.', 'Os depoimentos não estão disponíveis no momento.', 'Erfahrungsberichte sind gerade nicht verfügbar.'),
'Lo que dicen de POLYMER': ('What people say about POLYMER', 'Ce qu’on dit de POLYMER', 'Cosa dicono di POLYMER', 'O que dizem da POLYMER', 'Was man über POLYMER sagt'),
'Conoce al equipo que construyó POLYMER y lo que opinan nuestros clientes.': ('Meet the team that built POLYMER and what our customers think.', "Découvrez l'équipe qui a créé POLYMER et l'avis de nos clients.", 'Conosci il team che ha creato POLYMER e cosa pensano i nostri clienti.', 'Conheça a equipe que criou a POLYMER e o que nossos clientes acham.', 'Lerne das Team kennen, das POLYMER gebaut hat, und was unsere Kunden denken.'),
'Testimonios del carrusel': ('Carousel testimonials', 'Témoignages du carrousel', 'Testimonianze del carosello', 'Depoimentos do carrossel', 'Erfahrungsberichte im Karussell'),
'Autor': ('Author', 'Auteur', 'Autore', 'Autor', 'Autor'),
'Estrellas': ('Stars', 'Étoiles', 'Stelle', 'Estrelas', 'Sterne'),
'Testimonio': ('Testimonial', 'Témoignage', 'Testimonianza', 'Depoimento', 'Erfahrungsbericht'),
'Visible': ('Visible', 'Visible', 'Visibile', 'Visível', 'Sichtbar'),
'Testimonio oculto': ('Testimonial hidden', 'Témoignage masqué', 'Testimonianza nascosta', 'Depoimento oculto', 'Erfahrungsbericht ausgeblendet'),
'Testimonio visible': ('Testimonial visible', 'Témoignage visible', 'Testimonianza visibile', 'Depoimento visível', 'Erfahrungsbericht sichtbar'),
'Equipo': ('Team', 'Équipe', 'Team', 'Equipe', 'Team'),
'Sin testimonios': ('No testimonials', 'Aucun témoignage', 'Nessuna testimonianza', 'Sem depoimentos', 'Keine Erfahrungsberichte'),
'Los testimonios de los clientes aparecerán aquí.': ("Customers' testimonials will appear here.", 'Les témoignages des clients apparaîtront ici.', 'Le testimonianze dei clienti appariranno qui.', 'Os depoimentos dos clientes aparecerão aqui.', 'Die Erfahrungsberichte der Kunden erscheinen hier.'),
'No se pudieron cargar los testimonios': ('Testimonials could not be loaded', 'Impossible de charger les témoignages', 'Impossibile caricare le testimonianze', 'Não foi possível carregar os depoimentos', 'Erfahrungsberichte konnten nicht geladen werden'),
'Iniciar sesión con Google': ('Sign in with Google', 'Se connecter avec Google', 'Accedi con Google', 'Entrar com Google', 'Mit Google anmelden'),
'Ya tenías una cuenta POLYMER: confirma el código que te enviamos.': ('You already had a POLYMER account: confirm the code we sent you.', 'Vous aviez déjà un compte POLYMER : confirmez le code que nous vous avons envoyé.', 'Avevi già un account POLYMER: conferma il codice che ti abbiamo inviato.', 'Você já tinha uma conta POLYMER: confirme o código que enviamos.', 'Du hattest bereits ein POLYMER-Konto: Bestätige den Code, den wir dir geschickt haben.'),
'Inicia sesión para dejar tu testimonio': ('Sign in to leave your testimonial', 'Connectez-vous pour laisser votre témoignage', 'Accedi per lasciare la tua testimonianza', 'Entre para deixar seu depoimento', 'Melde dich an, um deinen Erfahrungsbericht zu hinterlassen'),
'rating: elige de 1 a 5 estrellas': ('rating: choose 1 to 5 stars', 'rating : choisissez de 1 à 5 étoiles', 'rating: scegli da 1 a 5 stelle', 'rating: escolha de 1 a 5 estrelas', 'rating: wähle 1 bis 5 Sterne'),
'Cuenta no encontrada': ('Account not found', 'Compte introuvable', 'Account non trovato', 'Conta não encontrada', 'Konto nicht gefunden'),
'Testimonio no encontrado': ('Testimonial not found', 'Témoignage introuvable', 'Testimonianza non trovata', 'Depoimento não encontrado', 'Erfahrungsbericht nicht gefunden'),
'No hay una cuenta POLYMER con esa cuenta de Google: regístrate primero': ('There is no POLYMER account for that Google account: sign up first', "Il n'y a pas de compte POLYMER pour ce compte Google : inscrivez-vous d'abord", "Non esiste un account POLYMER per questo account Google: registrati prima", 'Não há uma conta POLYMER para essa conta do Google: cadastre-se primeiro', 'Es gibt kein POLYMER-Konto für dieses Google-Konto: Registriere dich zuerst'),
'kind: obligatorio': ('kind: required', 'kind : obligatoire', 'kind: obbligatorio', 'kind: obrigatório', 'kind: erforderlich'),
'name: escribe tu nombre': ('name: enter your name', 'name : saisissez votre nom', 'name: inserisci il tuo nome', 'name: digite seu nome', 'name: gib deinen Namen ein'),
'linkedinUrl: usa la dirección de tu perfil, por ejemplo https://www.linkedin.com/in/tu-nombre': ('linkedinUrl: use your profile address, for example https://www.linkedin.com/in/your-name', "linkedinUrl : utilisez l'adresse de votre profil, par exemple https://www.linkedin.com/in/votre-nom", "linkedinUrl: usa l'indirizzo del tuo profilo, ad esempio https://www.linkedin.com/in/il-tuo-nome", 'linkedinUrl: use o endereço do seu perfil, por exemplo https://www.linkedin.com/in/seu-nome', 'linkedinUrl: verwende die Adresse deines Profils, z. B. https://www.linkedin.com/in/dein-name'),
'rating: de 1 a 5 estrellas': ('rating: 1 to 5 stars', 'rating : de 1 à 5 étoiles', 'rating: da 1 a 5 stelle', 'rating: de 1 a 5 estrelas', 'rating: 1 bis 5 Sterne'),
'userId: un testimonio de cliente necesita su cuenta': ("userId: a customer testimonial needs the customer's account", 'userId : un témoignage client nécessite son compte', "userId: una testimonianza cliente richiede il suo account", 'userId: um depoimento de cliente precisa da conta', 'userId: Ein Kundenbericht braucht das Kundenkonto'),
}

LANGS = ('en', 'fr', 'it', 'pt', 'de')

if __name__ == '__main__':
    root = Path(__file__).resolve().parents[2] / 'services/order-api/src/main/resources/static/i18n'
    unused = set(sys.argv[1:])
    for i, lang in enumerate(LANGS):
        path = root / f'{lang}.json'
        data = json.loads(path.read_text(encoding='utf-8'))
        for es, values in T.items():
            data.setdefault(es, values[i])
        for key in unused:
            data.pop(key, None)
        path.write_text(json.dumps(dict(sorted(data.items())), ensure_ascii=False, indent=1) + '\n', encoding='utf-8')
    print(f'{len(T)} textos v9 en {len(LANGS)} idiomas')
