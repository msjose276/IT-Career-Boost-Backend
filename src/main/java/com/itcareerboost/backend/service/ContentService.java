package com.itcareerboost.backend.service;

import com.itcareerboost.backend.dto.DashboardResponse;
import com.itcareerboost.backend.dto.StateResponse;
import com.itcareerboost.backend.model.Article;
import com.itcareerboost.backend.model.ArticleStatus;
import com.itcareerboost.backend.model.Category;
import com.itcareerboost.backend.model.SearchEvent;
import com.itcareerboost.backend.model.Tag;
import com.itcareerboost.backend.util.Slug;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ContentService {
  private final List<Article> articles = new ArrayList<>();
  private final List<Category> categories = new ArrayList<>();
  private final List<Tag> tags = new ArrayList<>();
  private final List<SearchEvent> searches = new ArrayList<>();
  private final List<Article> deletedArticles = new ArrayList<>();

  @PostConstruct
  void seed() {
    seedCategories();
    seedTags();
    seedArticles();
  }

  public synchronized StateResponse publicState() {
    return new StateResponse(
        articles.stream().filter(article -> article.getStatus() == ArticleStatus.published).toList(),
        List.copyOf(categories),
        List.copyOf(tags));
  }

  public synchronized StateResponse adminState() {
    return new StateResponse(List.copyOf(articles), List.copyOf(categories), List.copyOf(tags));
  }

  public synchronized List<Article> findArticles(String q, String category, String tag, String sort) {
    if (q != null && !q.isBlank()) {
      trackSearch(q);
    }
    return articles.stream()
        .filter(article -> article.getStatus() == ArticleStatus.published)
        .filter(article -> matches(article, q, category, tag))
        .sorted(articleComparator(sort))
        .toList();
  }

  public synchronized Article findPublishedBySlug(String slug) {
    Article article = articles.stream()
        .filter(item -> item.getStatus() == ArticleStatus.published)
        .filter(item -> item.getSlug().equals(slug))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Article not found."));
    article.setViews(article.getViews() + 1);
    return article;
  }

  public synchronized Article saveArticle(Article incoming) {
    incoming.setSlug(Slug.from(incoming.getSlug().isBlank() ? incoming.getTitle() : incoming.getSlug()));
    if (incoming.getId() == null || incoming.getId().isBlank()) {
      incoming.setId(UUID.randomUUID().toString());
    }
    ensureTags(incoming.getTags());
    Optional<Article> existing = articles.stream().filter(article -> article.getId().equals(incoming.getId())).findFirst();
    existing.ifPresentOrElse(article -> articles.set(articles.indexOf(article), incoming), () -> articles.add(0, incoming));
    return incoming;
  }

  public synchronized void deleteArticle(String id) {
    Article article = articles.stream()
        .filter(item -> item.getId().equals(id))
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Article not found."));
    articles.remove(article);
    deletedArticles.add(article);
  }

  public synchronized Category addCategory(Category incoming) {
    Category category = new Category(Slug.from(incoming.name()), incoming.name(), incoming.description());
    categories.add(category);
    return category;
  }

  public synchronized Tag addTag(Tag incoming) {
    Tag tag = new Tag(Slug.from(incoming.name()), incoming.name());
    if (tags.stream().noneMatch(existing -> existing.id().equals(tag.id()))) {
      tags.add(tag);
    }
    return tag;
  }

  public synchronized DashboardResponse dashboard() {
    List<Article> published = articles.stream().filter(article -> article.getStatus() == ArticleStatus.published).toList();
    long totalViews = articles.stream().mapToLong(Article::getViews).sum();
    List<Article> mostViewed = articles.stream()
        .sorted(Comparator.comparingLong(Article::getViews).reversed())
        .limit(5)
        .toList();
    List<Article> recent = articles.stream()
        .sorted(Comparator.comparing((Article article) -> article.getPublishedAt() == null ? LocalDate.MIN : article.getPublishedAt()).reversed())
        .limit(5)
        .toList();
    List<DashboardResponse.CategoryMetric> categoryMetrics = categories.stream()
        .map(category -> {
          List<Article> categoryArticles = articles.stream().filter(article -> article.getCategory().equals(category.id())).toList();
          return new DashboardResponse.CategoryMetric(
              category.id(),
              category.name(),
              categoryArticles.size(),
              categoryArticles.stream().mapToLong(Article::getViews).sum());
        })
        .toList();
    return new DashboardResponse(
        articles.size(),
        published.size(),
        totalViews,
        searches.size(),
        mostViewed,
        recent,
        categoryMetrics,
        searches.stream().limit(8).toList());
  }

  private boolean matches(Article article, String q, String category, String tag) {
    boolean matchesCategory = category == null || category.isBlank() || article.getCategory().equals(category);
    boolean matchesTag = tag == null || tag.isBlank() || article.getTags().contains(tag);
    if (!matchesCategory || !matchesTag) {
      return false;
    }
    if (q == null || q.isBlank()) {
      return true;
    }
    String query = q.toLowerCase(Locale.US);
    String categoryName = categories.stream()
        .filter(item -> item.id().equals(article.getCategory()))
        .map(Category::name)
        .findFirst()
        .orElse("");
    String haystack = String.join(" ",
            article.getTitle(),
            article.getSummary(),
            article.getContent().replaceAll("<[^>]+>", " "),
            categoryName,
            String.join(" ", article.getTags()))
        .toLowerCase(Locale.US);
    return haystack.contains(query);
  }

  private Comparator<Article> articleComparator(String sort) {
    if ("popular".equals(sort) || "views".equals(sort)) {
      return Comparator.comparingLong(Article::getViews).reversed();
    }
    return Comparator.comparing((Article article) -> article.getPublishedAt() == null ? LocalDate.MIN : article.getPublishedAt()).reversed();
  }

  private void trackSearch(String q) {
    searches.add(0, new SearchEvent(q, Instant.now()));
    while (searches.size() > 30) {
      searches.remove(searches.size() - 1);
    }
  }

  private void ensureTags(List<String> names) {
    names.forEach(name -> {
      String id = Slug.from(name);
      if (tags.stream().noneMatch(tag -> tag.id().equals(id))) {
        tags.add(new Tag(id, name));
      }
    });
  }

  private void seedCategories() {
    addSeedCategory("software-engineering", "Software Engineering", "Technical growth, architecture, code quality, and influence.");
    addSeedCategory("product-management", "Product Management", "Strategy, discovery, prioritization, and cross-functional leadership.");
    addSeedCategory("engineering-management", "Engineering Management", "Team building, coaching, performance, hiring, and delivery health.");
    addSeedCategory("leadership", "Leadership", "Communication, executive presence, stakeholder trust, and change leadership.");
    addSeedCategory("cloud-computing", "Cloud Computing", "Cloud skills, platform thinking, reliability, and cost-aware architecture.");
    addSeedCategory("devops", "DevOps", "Delivery systems, automation, incident response, and operational excellence.");
    addSeedCategory("cybersecurity", "Cybersecurity", "Security careers, risk communication, governance, and practical controls.");
    addSeedCategory("data-engineering", "Data Engineering", "Data platforms, reliability, quality, and analytics enablement.");
    addSeedCategory("artificial-intelligence", "Artificial Intelligence", "AI careers, responsible adoption, and applied learning paths.");
    addSeedCategory("career-growth", "Career Growth", "Promotion, personal brand, networking, salary, and long-term career design.");
    addSeedCategory("interview-preparation", "Interview Preparation", "Resume, portfolio, system design, behavioral interviews, and negotiation.");
    addSeedCategory("communication-skills", "Communication Skills", "Writing, presenting, feedback, alignment, and difficult conversations.");
  }

  private void seedTags() {
    List.of("Career Tips", "Promotion", "Resume", "Interview", "Leadership", "Networking", "Salary Negotiation",
        "Product Strategy", "Stakeholder Management", "Team Leadership", "Performance Reviews", "System Design", "Cloud", "AI")
        .forEach(name -> tags.add(new Tag(Slug.from(name), name)));
  }

  private void seedArticles() {
    seedCategoryArticles("software-engineering", List.of(
        seed("How to Get Promoted as a Software Engineer", "Build a promotion case with scope evidence, measurable impact, and manager alignment.", List.of("Promotion", "Career Tips", "Leadership"), "Maya Chen", "photo-1552664730-d307ca884978", "Promotion happens when your current work already looks like the next level.", "Translate the level rubric into visible behaviors, then collect proof each week.", "Track business impact, mentorship, technical decisions, and cross-team influence."),
        seed("Writing Design Docs That Win Trust", "Use design documents to clarify tradeoffs, invite review, and reduce expensive surprises.", List.of("System Design", "Communication Skills", "Leadership"), "Owen Brooks", "photo-1516321318423-f06f85e504b3", "A strong design doc is a decision tool, not a paperwork ritual.", "Start with context, constraints, options, risks, and a recommended path.", "Measure success by faster reviews, fewer reversals, and clearer ownership."),
        seed("Debugging Production Issues Without Panic", "A calm incident workflow for isolating symptoms, protecting users, and learning afterward.", List.of("DevOps", "Career Tips", "Leadership"), "Leah Morgan", "photo-1515879218367-8466d910aaa4", "The best engineers slow the room down when systems are failing.", "Separate facts from theories, preserve evidence, and communicate status in plain language.", "Review time to detection, time to mitigation, and the follow-up fixes that prevent repeats."),
        seed("From Ticket Taker to Problem Owner", "Move beyond assigned tasks by understanding customer pain, product goals, and system risk.", List.of("Career Tips", "Promotion", "Product Strategy"), "Samir Patel", "photo-1519389950473-47ba0277781c", "Career growth accelerates when engineers own outcomes instead of isolated tickets.", "Ask why the work matters before debating how to implement it.", "Track decisions that reduce rework, unblock partners, or improve customer value."),
        seed("Refactoring Legacy Code With Business Support", "Make technical debt visible in terms leaders understand: risk, speed, reliability, and cost.", List.of("System Design", "Stakeholder Management", "Leadership"), "Nina Brooks", "photo-1498050108023-c5249f4df085", "Legacy systems improve when refactoring is connected to business outcomes.", "Name the pain, limit the scope, and ship improvements alongside product work.", "Measure deploy frequency, defect rate, onboarding time, and incident reduction."),
        seed("The Engineer AI Cannot Replace", "A practical guide to building judgment, context, and trust that AI tools cannot fully automate.", List.of("AI", "Career Tips", "Leadership"), "Editorial Team", "photo-1497366754035-f200968a6e72", "AI can speed up routine work, but it cannot own context, accountability, and trust.", "Develop judgment by connecting code decisions to customer needs and long-term maintainability.", "Measure your value by better decisions, fewer surprises, and stronger team execution."),
        seed("Code Review as a Leadership Skill", "Turn reviews into coaching, risk management, and shared engineering standards.", List.of("Leadership", "Communication Skills", "Team Leadership"), "Jordan Patel", "photo-1556761175-b413da4baf72", "Code review is one of the most visible ways engineers shape team quality.", "Review for correctness, clarity, operational risk, and future maintainability.", "Watch review cycle time, defect trends, and whether teammates become more independent.")));

    seedCategoryArticles("product-management", List.of(
        seed("Product Strategy Fundamentals for New PMs", "Connect customer problems, business goals, bets, and measurable outcomes.", List.of("Product Strategy", "Stakeholder Management", "Leadership"), "Priya Shah", "photo-1551836022-d5d88e9218df", "Good strategy explains what you will do, what you will not do, and why now.", "Start with a sharp diagnosis before discussing roadmap options.", "Measure whether teams can use the strategy to make better tradeoffs."),
        seed("How to Prioritize When Everything Looks Important", "A simple prioritization model for balancing customer value, effort, risk, and timing.", List.of("Product Strategy", "Stakeholder Management", "Career Tips"), "Elena Cruz", "photo-1454165804606-c3d57bc86b40", "Prioritization is the art of making constraints explicit.", "Score options by customer pain, business leverage, confidence, and delivery complexity.", "Measure decision speed, stakeholder alignment, and shipped outcomes."),
        seed("Discovery Interviews That Produce Better Roadmaps", "Learn how to ask questions that reveal behavior, urgency, and unmet needs.", List.of("Product Strategy", "Communication Skills", "Interview"), "Marcus Lee", "photo-1556761175-5973dc0f32e7", "Discovery is strongest when it studies real behavior instead of opinions.", "Ask about recent situations, workarounds, consequences, and decision criteria.", "Measure patterns found, assumptions retired, and roadmap changes caused by evidence."),
        seed("Working With Engineering Without Losing Trust", "Build healthy tension around scope, quality, sequencing, and outcomes.", List.of("Stakeholder Management", "Leadership", "Communication Skills"), "Avery Kim", "photo-1551434678-e076c223a692", "Great PMs make engineering judgment easier to use, not harder to hear.", "Bring context early, invite technical options, and make tradeoffs visible.", "Measure fewer late surprises, clearer scope decisions, and healthier planning."),
        seed("Metrics That Help Product Teams Learn", "Choose product metrics that connect behavior, value, and business health.", List.of("Product Strategy", "Data Engineering", "Career Tips"), "Priya Shah", "photo-1460925895917-afdab827c52f", "A useful metric changes what the team learns and how it decides.", "Pair outcome metrics with guardrails so growth does not hide damage.", "Measure decisions improved, experiments clarified, and customer behavior changed."),
        seed("Roadmap Communication for Executive Reviews", "Present product plans with clarity, confidence, and honest uncertainty.", List.of("Stakeholder Management", "Leadership", "Communication Skills"), "Daniel Park", "photo-1542744173-8e7e53415bb0", "Executives need the logic behind the roadmap more than a list of features.", "Frame bets around outcomes, risks, dependencies, and decision points.", "Measure whether leaders can repeat the strategy and unblock the next step."),
        seed("Becoming a Product Manager From a Technical Role", "A transition guide for engineers, analysts, and technical specialists moving into PM.", List.of("Career Tips", "Product Strategy", "Networking"), "Mina Okafor", "photo-1552664730-d307ca884978", "Technical backgrounds help PMs when they are paired with customer empathy.", "Build proof through discovery notes, prioritization memos, and cross-functional projects.", "Measure portfolio strength, stakeholder feedback, and interview signal.")));

    seedCategoryArticles("engineering-management", List.of(
        seed("Transitioning from Engineer to Engineering Manager", "Understand how output changes when your work becomes team performance and people growth.", List.of("Team Leadership", "Performance Reviews", "Leadership"), "Alex Rivera", "photo-1521737604893-d14cc237f11d", "Management feels strange at first because your output becomes indirect.", "Protect one-on-ones, clarify priorities, and coach engineers toward larger ownership.", "Measure team health, delivery predictability, growth, and decision flow."),
        seed("One-on-Ones That Actually Develop Engineers", "Run conversations that surface goals, blockers, energy, and feedback.", List.of("Team Leadership", "Communication Skills", "Career Tips"), "Rachel Stone", "photo-1557804506-669a67965ba0", "One-on-ones are for development and trust, not status recitation.", "Ask about energy, friction, learning goals, and the feedback the person needs.", "Measure follow-through, engagement, and whether engineers own clearer growth plans."),
        seed("Managing Performance Without Surprises", "Create fair performance conversations with evidence, expectations, and early feedback.", List.of("Performance Reviews", "Team Leadership", "Leadership"), "Andre Wilson", "photo-1551836022-deb4988cc6c0", "Performance management breaks down when feedback arrives too late.", "Set expectations early, document examples, and separate impact from intent.", "Measure fewer review surprises and faster behavior change."),
        seed("Hiring Engineers With a Better Interview Loop", "Design interviews that test real work, reduce bias, and improve signal.", List.of("Interview", "Team Leadership", "Leadership"), "Tara Singh", "photo-1521791136064-7986c2920216", "A hiring loop should predict the work people will actually do.", "Define competencies before questions and calibrate interviewers after each round.", "Measure quality of hire, candidate experience, and interviewer consistency."),
        seed("Planning Capacity Without Burning Out the Team", "Balance ambition with sustainable delivery and realistic constraints.", List.of("Team Leadership", "DevOps", "Stakeholder Management"), "Alex Rivera", "photo-1551434678-e076c223a692", "Capacity planning is a trust contract between teams and stakeholders.", "Reserve space for support, incidents, reviews, and uncertainty before committing.", "Measure missed commitments, carryover, interrupts, and team energy."),
        seed("Giving Feedback Engineers Can Use", "Make feedback specific, timely, behavioral, and tied to outcomes.", List.of("Communication Skills", "Performance Reviews", "Leadership"), "Mei Tan", "photo-1522202176988-66273c2fd55f", "Useful feedback helps someone see a behavior they can change.", "Name the situation, behavior, impact, and next experiment.", "Measure whether the person can restate and act on the feedback."),
        seed("Building Senior Engineers Through Delegation", "Delegate ownership in ways that grow judgment instead of just moving tasks.", List.of("Promotion", "Team Leadership", "Leadership"), "Carlos Mendes", "photo-1517245386807-bb43f82c33c4", "Delegation is development when it includes context, authority, and feedback.", "Assign outcomes, clarify decision boundaries, and review learning after delivery.", "Measure independent decisions, broader influence, and reduced manager bottlenecks.")));

    seedCategoryArticles("leadership", List.of(
        seed("Technical Leadership Without a Management Title", "Influence teams through clarity, decisions, and calm execution without becoming a people manager.", List.of("Leadership", "Stakeholder Management", "Career Tips"), "Jordan Patel", "photo-1556761175-b413da4baf72", "Technical leadership is the ability to improve outcomes beyond your own keyboard.", "Write tradeoffs down, make risks visible, and help teams choose a path.", "Measure decision quality, coordination cost, and follow-through."),
        seed("Executive Presence for Technical Professionals", "Communicate with clarity, confidence, and useful context in senior rooms.", List.of("Leadership", "Communication Skills", "Stakeholder Management"), "Nora Ellis", "photo-1557804506-669a67965ba0", "Executive presence is not performance; it is calm usefulness under pressure.", "Lead with the decision needed, then explain options, risks, and recommendation.", "Measure whether leaders understand, decide, and trust your judgment."),
        seed("Leading Through Ambiguity", "Help teams move when goals are unclear and information is incomplete.", List.of("Leadership", "Product Strategy", "Communication Skills"), "Ibrahim Khan", "photo-1497366811353-6870744d04b2", "Ambiguity is normal in meaningful work; confusion does not have to be.", "Separate known facts, assumptions, risks, and next learning steps.", "Measure speed of alignment and the number of assumptions retired."),
        seed("Building Trust With Difficult Stakeholders", "Repair strained relationships through transparency, consistency, and useful tradeoffs.", List.of("Stakeholder Management", "Leadership", "Communication Skills"), "Maya Chen", "photo-1556761175-5973dc0f32e7", "Trust grows when people can predict how you handle pressure.", "Acknowledge constraints, explain tradeoffs, and follow up before being chased.", "Measure fewer escalations and more early collaboration."),
        seed("Decision Memos for High-Stakes Work", "Use concise memos to align people before major technical or product choices.", List.of("Leadership", "System Design", "Communication Skills"), "Owen Brooks", "photo-1516321318423-f06f85e504b3", "A decision memo turns scattered opinion into a shared operating record.", "State context, options, recommendation, risks, and the owner of the decision.", "Measure review speed and whether future teams understand why the choice was made."),
        seed("The First 90 Days in a Tech Leadership Role", "Build credibility quickly by listening, diagnosing, and choosing early wins.", List.of("Leadership", "Career Tips", "Team Leadership"), "Rachel Stone", "photo-1551836022-deb4988cc6c0", "The first months should produce trust before dramatic change.", "Listen for patterns, map stakeholders, and choose one visible problem to improve.", "Measure relationship strength, clarity of diagnosis, and credibility of early wins."),
        seed("Leading Change Without Creating Chaos", "Roll out process or technical change with adoption, feedback, and guardrails.", List.of("Leadership", "Stakeholder Management", "Communication Skills"), "Daniel Park", "photo-1542744173-8e7e53415bb0", "Change fails when it ignores the people who must live with it.", "Explain the why, pilot with a small group, and adapt based on friction.", "Measure adoption, support requests, and whether the new behavior sticks.")));

    seedCategoryArticles("cloud-computing", List.of(
        seed("Cloud Fundamentals for Career Switchers", "Understand compute, storage, networking, identity, and cost without drowning in jargon.", List.of("Cloud", "Career Tips", "System Design"), "Avery Kim", "photo-1451187580459-43490279c0fa", "Cloud fluency starts with the basic building blocks every platform shares.", "Map a simple web app across compute, storage, network, identity, and monitoring.", "Measure whether you can explain tradeoffs without naming a specific vendor first."),
        seed("Designing Cost-Aware Cloud Systems", "Make cost a design constraint alongside reliability, performance, and security.", List.of("Cloud", "System Design", "DevOps"), "Liam Carter", "photo-1518770660439-4636190af475", "Cloud bills reveal architecture decisions faster than most dashboards.", "Tag resources, model usage, and review expensive paths before scaling.", "Measure unit cost, idle spend, and cost regressions after launches."),
        seed("Reliability Basics for Cloud Engineers", "Use SLOs, redundancy, observability, and incident review to improve service health.", List.of("Cloud", "DevOps", "System Design"), "Tara Singh", "photo-1504384308090-c894fdcc538d", "Reliability is designed through explicit expectations and tested failure modes.", "Define service-level objectives and rehearse the most likely failure scenarios.", "Measure error budgets, recovery time, and repeat incident rate."),
        seed("Cloud Certification Study Plan That Works", "Turn certification prep into practical skill instead of memorized trivia.", List.of("Cloud", "Career Tips", "Interview"), "Noah Reed", "photo-1498050108023-c5249f4df085", "Certifications help most when they are attached to hands-on projects.", "Build small labs for networking, identity, storage, and deployment workflows.", "Measure practice exam improvement and the ability to explain design choices."),
        seed("Identity and Access Management for Beginners", "Learn IAM concepts that protect systems and prevent common cloud mistakes.", List.of("Cloud", "Cybersecurity", "Career Tips"), "Nora Ellis", "photo-1563986768494-4dee2763ff3f", "Most cloud security failures begin with unclear access boundaries.", "Use least privilege, role-based access, and temporary credentials where possible.", "Measure unused permissions, policy drift, and audit readiness."),
        seed("Migrating Legacy Apps to the Cloud", "Plan migrations around risk, dependencies, observability, and rollback paths.", List.of("Cloud", "System Design", "Stakeholder Management"), "Carlos Mendes", "photo-1519389950473-47ba0277781c", "Cloud migration is a business change wrapped in technical work.", "Inventory dependencies, choose a migration pattern, and prove rollback early.", "Measure downtime, performance, cost, and post-migration support load."),
        seed("Cloud Portfolio Projects Recruiters Understand", "Build demonstrable projects that show architecture, automation, and operational thinking.", List.of("Cloud", "Resume", "Career Tips"), "Mina Okafor", "photo-1461749280684-dccba630e2f6", "A cloud portfolio should show how you think, not only what you clicked.", "Document architecture diagrams, tradeoffs, deployment steps, and monitoring.", "Measure whether a reviewer can understand the project in five minutes.")));

    seedCategoryArticles("devops", List.of(
        seed("DevOps Foundations Beyond the Buzzword", "Understand collaboration, automation, feedback loops, and operational ownership.", List.of("DevOps", "Career Tips", "Leadership"), "Leah Morgan", "photo-1515879218367-8466d910aaa4", "DevOps is a way to shorten the distance between code and reliable value.", "Improve one handoff, one feedback loop, and one repeatable deployment step.", "Measure deployment frequency, recovery time, and escaped defects."),
        seed("Building a CI Pipeline Recruiters Respect", "Create automated checks that demonstrate quality, repeatability, and engineering discipline.", List.of("DevOps", "Resume", "System Design"), "Owen Brooks", "photo-1516321318423-f06f85e504b3", "A useful CI pipeline makes risk visible before code reaches users.", "Run formatting, tests, security checks, and artifact creation on every change.", "Measure build duration, failure clarity, and blocked defects."),
        seed("Incident Response for New DevOps Engineers", "Respond to outages with structure, communication, and learning.", List.of("DevOps", "Communication Skills", "Leadership"), "Tara Singh", "photo-1504384308090-c894fdcc538d", "Incidents test both systems and teamwork.", "Assign roles, keep a timeline, communicate impact, and preserve follow-up ownership.", "Measure recovery time, customer impact, and completion of corrective actions."),
        seed("Infrastructure as Code Career Guide", "Use infrastructure as code to make environments repeatable, reviewable, and safer.", List.of("DevOps", "Cloud", "System Design"), "Liam Carter", "photo-1498050108023-c5249f4df085", "Infrastructure as code turns operational knowledge into versioned software.", "Start with a small environment and review changes like application code.", "Measure drift, provisioning time, and rollback confidence."),
        seed("Observability That Helps Teams Act", "Design logs, metrics, and traces around questions teams actually ask.", List.of("DevOps", "Data Engineering", "System Design"), "Rachel Stone", "photo-1460925895917-afdab827c52f", "Observability is not more dashboards; it is faster answers.", "Start with the user journey and define signals for latency, errors, and saturation.", "Measure time to understand incidents and noisy alert reduction."),
        seed("Release Management Without Heroics", "Create release habits that reduce risk and make delivery boring.", List.of("DevOps", "Team Leadership", "Stakeholder Management"), "Andre Wilson", "photo-1521791136064-7986c2920216", "Healthy releases depend on small changes, clear ownership, and rollback plans.", "Use feature flags, staged rollouts, and release notes that name risk.", "Measure failed deployments, rollback time, and stakeholder confidence."),
        seed("DevOps Interview Questions and Strong Answers", "Prepare stories around automation, reliability, incidents, and collaboration.", List.of("DevOps", "Interview", "Resume"), "Samir Patel", "photo-1551434678-e076c223a692", "DevOps interviews reward evidence of systems thinking and calm execution.", "Prepare examples for pipelines, incidents, monitoring, and cross-team influence.", "Measure answer quality with clear context, action, result, and learning.")));

    seedCategoryArticles("cybersecurity", List.of(
        seed("Cybersecurity Career Paths Explained", "Compare security analyst, engineer, GRC, cloud security, and appsec roles.", List.of("Cybersecurity", "Career Tips", "Networking"), "Nora Ellis", "photo-1563986768494-4dee2763ff3f", "Security careers are broader than monitoring alerts.", "Map roles by daily work, required depth, business exposure, and learning path.", "Measure fit through projects, conversations, and entry-level role requirements."),
        seed("Risk Communication for Security Professionals", "Explain security risk in language product, engineering, and executives can act on.", List.of("Cybersecurity", "Communication Skills", "Stakeholder Management"), "Ibrahim Khan", "photo-1542744173-8e7e53415bb0", "Security influence depends on making risk understandable and actionable.", "Frame issues around likelihood, impact, tradeoffs, and recommended next steps.", "Measure whether stakeholders make timely decisions without defensiveness."),
        seed("Building a Practical Home Security Lab", "Create hands-on learning projects for networking, detection, hardening, and incident response.", List.of("Cybersecurity", "Resume", "Career Tips"), "Noah Reed", "photo-1518770660439-4636190af475", "A lab turns security theory into evidence you can show.", "Document objectives, tools, findings, and lessons from each experiment.", "Measure portfolio clarity and how well projects map to target roles."),
        seed("Security Controls Engineers Should Understand", "Learn authentication, authorization, encryption, logging, and dependency hygiene.", List.of("Cybersecurity", "Software Engineering", "System Design"), "Maya Chen", "photo-1515879218367-8466d910aaa4", "Every engineer benefits from understanding common security controls.", "Apply secure defaults to one service and document the threat model.", "Measure reduced risky permissions, cleaner dependencies, and audit readiness."),
        seed("Preparing for a Security Analyst Interview", "Build stories around investigation, prioritization, communication, and learning.", List.of("Cybersecurity", "Interview", "Career Tips"), "Elena Cruz", "photo-1556761175-5973dc0f32e7", "Security analyst interviews test curiosity and disciplined thinking.", "Practice explaining alerts, triage logic, escalation, and evidence handling.", "Measure answers by clarity, prioritization, and business impact."),
        seed("Cloud Security Basics for IT Professionals", "Understand identity, network boundaries, secrets, monitoring, and shared responsibility.", List.of("Cybersecurity", "Cloud", "Career Tips"), "Avery Kim", "photo-1451187580459-43490279c0fa", "Cloud security begins with knowing which responsibilities are yours.", "Review IAM, network exposure, encryption, logging, and secrets for a sample app.", "Measure least-privilege progress and visibility into risky changes."),
        seed("GRC Skills That Make Security Programs Work", "Turn policies, controls, audits, and evidence into business trust.", List.of("Cybersecurity", "Stakeholder Management", "Communication Skills"), "Nina Brooks", "photo-1554224155-6726b3ff858f", "GRC is strongest when it connects controls to real operational behavior.", "Build evidence workflows that teams can sustain without last-minute chaos.", "Measure audit readiness, control ownership, and recurring findings.")));

    seedCategoryArticles("data-engineering", List.of(
        seed("Data Engineering Career Roadmap", "Learn the skills behind pipelines, warehouses, orchestration, quality, and analytics enablement.", List.of("Data Engineering", "Career Tips", "Resume"), "Sam Okafor", "photo-1460925895917-afdab827c52f", "Data engineering turns raw activity into reliable decision-making infrastructure.", "Build a pipeline project that ingests, transforms, validates, and serves data.", "Measure freshness, reliability, documentation, and stakeholder usefulness."),
        seed("Designing Reliable Data Pipelines", "Prevent silent failures with validation, monitoring, idempotency, and ownership.", List.of("Data Engineering", "DevOps", "System Design"), "Leah Morgan", "photo-1516321318423-f06f85e504b3", "Data pipelines fail quietly unless reliability is designed in.", "Validate inputs, track freshness, and make reruns safe.", "Measure failed jobs, late data, bad records, and time to recovery."),
        seed("SQL Skills for Analytics and Engineering Roles", "Move beyond basic queries into modeling, performance, and business interpretation.", List.of("Data Engineering", "Interview", "Career Tips"), "Marcus Lee", "photo-1551288049-bebda4e38f71", "SQL is a communication language between data and decisions.", "Practice joins, windows, aggregations, explain plans, and metric definitions.", "Measure query correctness, performance, and explanation clarity."),
        seed("Data Quality Checks That Prevent Bad Decisions", "Use tests, contracts, and ownership to keep business metrics trustworthy.", List.of("Data Engineering", "Product Strategy", "Stakeholder Management"), "Priya Shah", "photo-1551836022-d5d88e9218df", "Bad data can create confident wrong decisions.", "Define checks for completeness, uniqueness, validity, timeliness, and consistency.", "Measure incidents avoided, broken dashboards, and trust from business partners."),
        seed("From Analyst to Data Engineer", "Translate analysis skills into production pipelines, modeling, and platform thinking.", List.of("Data Engineering", "Career Tips", "Promotion"), "Mina Okafor", "photo-1497366811353-6870744d04b2", "Analysts already know the questions data must answer.", "Add software practices: version control, orchestration, tests, and deployment.", "Measure project reliability and readiness for engineering interviews."),
        seed("Explaining Data Architecture to Stakeholders", "Make warehouses, marts, lineage, and governance understandable to non-technical partners.", List.of("Data Engineering", "Communication Skills", "Stakeholder Management"), "Daniel Park", "photo-1542744173-8e7e53415bb0", "Data architecture needs a story that partners can use.", "Explain how data moves from source systems to decisions and where risk enters.", "Measure fewer metric disputes and faster stakeholder decisions."),
        seed("Portfolio Projects for Data Engineering Jobs", "Create projects that show ingestion, transformation, orchestration, quality, and documentation.", List.of("Data Engineering", "Resume", "Interview"), "Noah Reed", "photo-1461749280684-dccba630e2f6", "A good data portfolio shows reliability, not just charts.", "Publish diagrams, pipeline code, tests, sample data, and a clear README.", "Measure whether hiring managers can see the engineering decisions quickly.")));

    seedCategoryArticles("artificial-intelligence", List.of(
        seed("AI Career Paths for Technology Professionals", "Compare AI product, ML engineering, data science, platform, and governance roles.", List.of("AI", "Career Tips", "Product Strategy"), "Maya Chen", "photo-1677442136019-21780ecad995", "AI careers reward people who combine domain context with technical judgment.", "Choose a path by daily work, math depth, product exposure, and deployment responsibility.", "Measure fit through projects, role descriptions, and mentor feedback."),
        seed("Using AI Tools Without Losing Engineering Judgment", "Learn where AI helps, where it misleads, and how to review generated work.", List.of("AI", "Software Engineering", "Leadership"), "Jordan Patel", "photo-1676299081847-824916de030a", "AI tools are accelerators, not accountable teammates.", "Review generated code for context, security, tests, and maintainability.", "Measure time saved alongside defects caught and decisions improved."),
        seed("Prompting Skills for Workplace Productivity", "Write prompts that clarify context, constraints, examples, and desired output.", List.of("AI", "Communication Skills", "Career Tips"), "Elena Cruz", "photo-1674027444485-cec3da58eef4", "Good prompts are structured delegation.", "Provide role, goal, context, constraints, examples, and evaluation criteria.", "Measure fewer revisions and more useful first drafts."),
        seed("Responsible AI Basics for Product Teams", "Understand privacy, bias, evaluation, transparency, and human oversight.", List.of("AI", "Product Strategy", "Cybersecurity"), "Priya Shah", "photo-1677756119517-756a188d2d94", "Responsible AI is product quality under higher stakes.", "Define acceptable use, evaluation data, escalation paths, and user-facing expectations.", "Measure harm reports, model quality, and governance readiness."),
        seed("Building an AI Portfolio Project", "Create applied projects that show problem framing, evaluation, and user value.", List.of("AI", "Resume", "Interview"), "Noah Reed", "photo-1518770660439-4636190af475", "AI portfolio projects should prove that you can solve a real problem responsibly.", "Document the problem, data, model/tool choice, evaluation, and limitations.", "Measure usefulness, accuracy, latency, cost, and clarity of tradeoffs."),
        seed("AI Adoption for Engineering Managers", "Help teams use AI tools while protecting quality, security, and learning.", List.of("AI", "Engineering Management", "Team Leadership"), "Alex Rivera", "photo-1521737604893-d14cc237f11d", "AI adoption is a management problem as much as a tooling problem.", "Set guidelines for review, sensitive data, testing, and skill development.", "Measure productivity, defect rates, developer sentiment, and policy adherence."),
        seed("Interviewing for AI-Adjacent Roles", "Prepare for roles that require AI literacy without requiring deep research credentials.", List.of("AI", "Interview", "Career Tips"), "Samir Patel", "photo-1552664730-d307ca884978", "Many AI roles need practical judgment more than research expertise.", "Prepare stories about use cases, evaluation, risk, product impact, and collaboration.", "Measure answer quality by specificity and responsible tradeoff thinking.")));

    seedCategoryArticles("career-growth", List.of(
        seed("Building a Career Development Plan That Actually Moves", "Turn vague growth goals into quarterly skills, projects, feedback loops, and outcomes.", List.of("Career Tips", "Promotion", "Resume"), "Sam Okafor", "photo-1497366811353-6870744d04b2", "A useful career plan is small enough to execute and specific enough to create feedback.", "Pick one outcome and attach practice to real work.", "Measure progress with visible artifacts, feedback, and new responsibility."),
        seed("Salary Negotiation for Technology Professionals", "Prepare compensation conversations with market data, impact evidence, and collaborative framing.", List.of("Salary Negotiation", "Career Tips", "Networking"), "Nina Brooks", "photo-1554224155-6726b3ff858f", "Negotiation is a business conversation about scope, market value, and fit.", "Use market data, impact evidence, and a target range with rationale.", "Measure total package value and whether trust remains intact."),
        seed("Personal Branding for Engineers Who Hate Self-Promotion", "Build professional visibility through useful writing, demos, and community contribution.", List.of("Networking", "Career Tips", "Communication Skills"), "Mina Okafor", "photo-1497366754035-f200968a6e72", "Personal brand is simply what people trust you to be useful for.", "Share lessons, project notes, and practical examples from real work.", "Measure inbound conversations, referrals, and clarity of your professional story."),
        seed("Finding Mentors Without Making It Awkward", "Create low-pressure mentor relationships through specific questions and follow-through.", List.of("Networking", "Career Tips", "Leadership"), "Rachel Stone", "photo-1522202176988-66273c2fd55f", "Mentorship works best when the ask is specific and respectful.", "Ask for perspective on one decision, then report back on what you tried.", "Measure relationship depth and the quality of decisions improved."),
        seed("How to Recover From a Career Setback", "Rebuild momentum after layoffs, missed promotions, failed interviews, or difficult projects.", List.of("Career Tips", "Resume", "Interview"), "Andre Wilson", "photo-1521791136064-7986c2920216", "Setbacks are data points, not final verdicts.", "Separate what happened, what you learned, and what you will change next.", "Measure new applications, stronger stories, and restored consistency."),
        seed("Choosing Between Specialist and Generalist Paths", "Evaluate career direction by strengths, market demand, energy, and long-term options.", List.of("Career Tips", "Promotion", "Leadership"), "Carlos Mendes", "photo-1517245386807-bb43f82c33c4", "Both specialists and generalists can build exceptional careers.", "Compare paths by the problems you enjoy, the proof you can show, and market needs.", "Measure fit through project energy, feedback, and opportunity quality."),
        seed("Career Growth for Remote Tech Workers", "Stay visible, trusted, and connected when your work is distributed.", List.of("Career Tips", "Communication Skills", "Promotion"), "Leah Morgan", "photo-1504384308090-c894fdcc538d", "Remote growth requires intentional communication and visible outcomes.", "Write clearer updates, document decisions, and build relationships beyond your immediate team.", "Measure stakeholder trust, promotion evidence, and fewer visibility gaps.")));

    seedCategoryArticles("interview-preparation", List.of(
        seed("Behavioral Interview Stories for Tech Roles", "Build STAR stories that show judgment, ownership, collaboration, and learning.", List.of("Interview", "Career Tips", "Communication Skills"), "Elena Cruz", "photo-1556761175-5973dc0f32e7", "Behavioral interviews are evidence reviews, not personality quizzes.", "Prepare stories around conflict, ambiguity, failure, leadership, and impact.", "Measure clarity, specificity, and whether each story has a credible result."),
        seed("System Design Interview Study Plan", "Prepare for architecture interviews with requirements, tradeoffs, scaling, and reliability.", List.of("Interview", "System Design", "Software Engineering"), "Owen Brooks", "photo-1516321318423-f06f85e504b3", "System design interviews reward structured thinking under uncertainty.", "Practice requirements, data model, APIs, bottlenecks, failure modes, and tradeoffs.", "Measure whether your answer stays coherent as constraints change."),
        seed("Resume Improvements for Technology Professionals", "Rewrite resumes around outcomes, scope, tools, and measurable impact.", List.of("Resume", "Career Tips", "Interview"), "Nina Brooks", "photo-1554224155-6726b3ff858f", "A resume should make your impact easy to understand quickly.", "Lead bullets with action, scope, metric, and business relevance.", "Measure callback rate and whether interviewers ask about your strongest work."),
        seed("Portfolio Review Checklist for Developers", "Show projects with context, architecture, tradeoffs, tests, and deployment notes.", List.of("Resume", "Interview", "Software Engineering"), "Noah Reed", "photo-1461749280684-dccba630e2f6", "A portfolio should prove how you think when nobody is prompting you.", "Add README context, screenshots, diagrams, tests, and known limitations.", "Measure whether reviewers can understand the project without a live walkthrough."),
        seed("Negotiating Offers After the Final Interview", "Handle compensation conversations after you receive an offer.", List.of("Salary Negotiation", "Interview", "Career Tips"), "Maya Chen", "photo-1554224155-6726b3ff858f", "The best negotiation starts before you name a number.", "Clarify level, scope, range, competing factors, and timeline.", "Measure total compensation, role fit, and relationship quality after acceptance."),
        seed("Mock Interview Practice That Actually Helps", "Design practice sessions that improve structure, confidence, and feedback quality.", List.of("Interview", "Communication Skills", "Career Tips"), "Samir Patel", "photo-1552664730-d307ca884978", "Mock interviews help when they target specific behaviors.", "Practice one skill at a time and ask for feedback on evidence, structure, and pacing.", "Measure fewer rambling answers and stronger recovery after mistakes."),
        seed("Explaining Career Transitions in Interviews", "Tell a clear story when moving into tech, management, product, cloud, or security.", List.of("Interview", "Career Tips", "Resume"), "Mina Okafor", "photo-1497366811353-6870744d04b2", "Transitions need a narrative that connects past strengths to future contribution.", "Explain the reason, preparation, proof, and target role clearly.", "Measure whether interviewers see continuity instead of randomness.")));

    seedCategoryArticles("communication-skills", List.of(
        seed("Writing Status Updates Leaders Actually Read", "Share progress, risk, decisions, and asks in a format busy stakeholders can use.", List.of("Communication Skills", "Stakeholder Management", "Leadership"), "Daniel Park", "photo-1542744173-8e7e53415bb0", "A good status update reduces the need for a meeting.", "Lead with outcome, status, risk, next step, and any decision needed.", "Measure fewer follow-up questions and faster stakeholder response."),
        seed("Presenting Technical Work to Non-Technical Audiences", "Translate architecture, risk, and progress into language partners understand.", List.of("Communication Skills", "Leadership", "Stakeholder Management"), "Nora Ellis", "photo-1557804506-669a67965ba0", "Technical communication works when it respects the audience's decisions.", "Start with why it matters, then explain only the technical detail needed.", "Measure whether the audience can make the decision or support the work."),
        seed("Difficult Conversations at Work", "Handle conflict, disappointment, and disagreement with clarity and respect.", List.of("Communication Skills", "Leadership", "Career Tips"), "Rachel Stone", "photo-1522202176988-66273c2fd55f", "Avoided conversations usually become larger problems.", "Name the issue, impact, and desired next step without attacking identity.", "Measure whether trust and clarity improve after the conversation."),
        seed("Asking Better Questions in Technical Meetings", "Use questions to uncover assumptions, clarify risk, and improve decisions.", List.of("Communication Skills", "System Design", "Leadership"), "Owen Brooks", "photo-1516321318423-f06f85e504b3", "Good questions are a leadership tool.", "Ask what problem is being solved, what options were considered, and what could fail.", "Measure whether meetings end with clearer decisions and owners."),
        seed("Documentation Habits That Save Teams Time", "Write docs that make onboarding, operations, and decisions easier.", List.of("Communication Skills", "DevOps", "Team Leadership"), "Leah Morgan", "photo-1515879218367-8466d910aaa4", "Documentation is leverage when it answers repeated questions.", "Document decisions, workflows, ownership, and troubleshooting steps close to the work.", "Measure fewer interruptions and faster onboarding."),
        seed("Feedback Conversations for Peer Engineers", "Give peer feedback that is specific, useful, and relationship-safe.", List.of("Communication Skills", "Leadership", "Career Tips"), "Mei Tan", "photo-1521737604893-d14cc237f11d", "Peer feedback works when it is grounded in shared outcomes.", "Describe the situation, behavior, impact, and a practical alternative.", "Measure whether the next collaboration improves."),
        seed("Meeting Facilitation for Technical Teams", "Run meetings that produce decisions, alignment, and ownership instead of drift.", List.of("Communication Skills", "Team Leadership", "Stakeholder Management"), "Andre Wilson", "photo-1521791136064-7986c2920216", "A meeting earns its time when it creates a decision or shared understanding.", "Set the purpose, decision needed, inputs, and owner before the meeting starts.", "Measure shorter meetings, clearer owners, and fewer repeated discussions.")));
  }

  private void seedCategoryArticles(String category, List<SeedArticle> seeds) {
    for (int index = 0; index < seeds.size(); index++) {
      SeedArticle seed = seeds.get(index);
      int sequence = articles.size();
      articles.add(article(
          seed.title(),
          Slug.from(seed.title()),
          seed.summary(),
          category,
          seed.tags(),
          seed.author(),
          ArticleStatus.published,
          LocalDate.of(2026, 5, 30).minusDays(sequence * 3L).toString(),
          6400 + (long) (84 - sequence) * 175,
          index == 0,
          index <= 1,
          imageFor(seed.imageId()),
          seed.title(),
          seed.summary(),
          content(seed, category)));
    }
  }

  private SeedArticle seed(String title, String summary, List<String> tags, String author, String imageId,
      String intro, String practice, String measure) {
    return new SeedArticle(title, summary, tags, author, imageId, intro, practice, measure);
  }

  private String content(SeedArticle seed, String category) {
    String angle = categoryAngle(category);
    String skill = primarySkill(seed);
    String firstTag = seed.tags().isEmpty() ? "career growth" : seed.tags().get(0).toLowerCase(Locale.US);

    return "<p>" + seed.intro() + " This guide turns that idea into a practical operating system you can use at work this week, not just advice to agree with and forget.</p>"
        + "<p>" + seed.summary() + " The goal is to help you create visible evidence: better decisions, clearer communication, stronger delivery, and fewer surprises for the people depending on you.</p>"
        + "<h2>When this shows up in real work</h2>"
        + "<p>" + angle + " You will usually notice the need for this skill when the team is dealing with ambiguity, pressure, handoffs, or a decision that affects more than one person. In those moments, useful professionals do three things: they clarify the goal, name the constraint, and make the next action easier for everyone else.</p>"
        + "<p>A common mistake is waiting until you feel fully authorized. You do not need permission to improve the quality of a conversation, write down a tradeoff, ask a sharper question, or reduce confusion around a decision. Small acts of ownership compound quickly because they change how others experience working with you.</p>"
        + "<h2>What good looks like</h2>"
        + "<ul>"
        + "<li>You can explain the business or user reason behind the work in plain language.</li>"
        + "<li>You separate facts, assumptions, risks, and preferences before recommending a path.</li>"
        + "<li>You make tradeoffs visible early, while there is still time to adjust.</li>"
        + "<li>You leave behind an artifact: a note, checklist, decision record, dashboard, pull request, or follow-up plan.</li>"
        + "<li>You help other people make better decisions even when you are not in the room.</li>"
        + "</ul>"
        + "<h2>A practical way to start</h2>"
        + "<p>" + seed.practice() + " Pick one active project or upcoming conversation and apply the skill there. Do not make this a separate self-improvement project; attach it to work that already matters so the feedback is immediate and real.</p>"
        + "<ol>"
        + "<li><strong>Define the outcome.</strong> Write one sentence that says what would be better if you handled this well.</li>"
        + "<li><strong>Map the audience.</strong> Identify who needs confidence, who needs detail, who needs a decision, and who needs to be protected from unnecessary noise.</li>"
        + "<li><strong>List the constraints.</strong> Include time, quality, budget, risk, dependencies, customer impact, and team capacity.</li>"
        + "<li><strong>Create the artifact.</strong> Draft the doc, checklist, metric, story, review note, or plan that makes your thinking inspectable.</li>"
        + "<li><strong>Ask for review.</strong> Invite one person to challenge the logic before the stakes are high.</li>"
        + "<li><strong>Close the loop.</strong> Share what changed, what shipped, what was learned, and what remains unresolved.</li>"
        + "</ol>"
        + "<h2>Questions to ask yourself</h2>"
        + "<ul>"
        + "<li>What problem am I actually trying to solve, and who feels the pain?</li>"
        + "<li>What would a stronger version of this work look like at the next career level?</li>"
        + "<li>Which risk is easiest to ignore because it is uncomfortable to discuss?</li>"
        + "<li>What evidence would convince a skeptical but fair reviewer that progress happened?</li>"
        + "<li>What can I document now so another person does not have to rediscover it later?</li>"
        + "</ul>"
        + "<h2>Example in practice</h2>"
        + "<p>Imagine you are applying this to " + escape(seed.title().toLowerCase(Locale.US)) + ". Instead of saying, \"I worked on " + firstTag + ",\" you would describe the situation, the constraint, the options considered, the decision made, and the result. For example: \"The team was blocked because the next step was unclear, so I summarized the tradeoffs, proposed a low-risk path, got feedback from the affected teams, and tracked the result after delivery.\" That kind of story is useful because it shows judgment, not just activity.</p>"
        + "<p>The same pattern works in a resume bullet, promotion packet, interview answer, design review, roadmap discussion, or one-on-one. Start with context, explain your action, and connect it to an outcome someone else cared about.</p>"
        + "<h2>Mistakes to avoid</h2>"
        + "<ul>"
        + "<li><strong>Confusing effort with impact.</strong> Long hours are not the same as better outcomes.</li>"
        + "<li><strong>Skipping stakeholder context.</strong> Good technical or career decisions still fail when the audience cannot understand the reason.</li>"
        + "<li><strong>Waiting too long to surface risk.</strong> Early risk sounds responsible; late risk sounds like an excuse.</li>"
        + "<li><strong>Using vague language.</strong> Words like improved, helped, and owned become stronger when paired with scope, metric, or decision quality.</li>"
        + "<li><strong>Trying to change everything at once.</strong> One focused behavior practiced consistently beats a long list of intentions.</li>"
        + "</ul>"
        + "<h2>How to measure progress</h2>"
        + "<p>" + seed.measure() + " Add one weekly review checkpoint. Ask what became clearer, what moved faster, what risk decreased, and what evidence you created. If nothing changed, shrink the next experiment until it is small enough to complete.</p>"
        + "<p>Useful evidence can include shorter review cycles, fewer repeated questions, better interview stories, cleaner handoffs, more specific feedback, stronger stakeholder trust, or a decision that stayed stable under pressure. The point is not to prove you are perfect; it is to make growth observable.</p>"
        + "<h2>30-day action plan</h2>"
        + "<ul>"
        + "<li><strong>Week 1:</strong> Choose one real situation where " + skill + " would improve the outcome. Write the goal, audience, constraints, and current risk.</li>"
        + "<li><strong>Week 2:</strong> Create a small artifact and get feedback from a peer, manager, customer, or stakeholder.</li>"
        + "<li><strong>Week 3:</strong> Apply the feedback, use the artifact in the actual work, and note what changed.</li>"
        + "<li><strong>Week 4:</strong> turn the result into a reusable example for your portfolio, promotion notes, interview bank, or team documentation.</li>"
        + "</ul>"
        + "<blockquote>The professionals who grow fastest are not the ones who collect the most advice. They are the ones who turn advice into visible behavior and visible behavior into evidence.</blockquote>";
  }

  private String categoryAngle(String category) {
    return switch (category) {
      case "software-engineering" -> "In software engineering, this often appears as a design decision, code review, incident, refactor, or ownership gap.";
      case "product-management" -> "In product management, this often appears when customer evidence, business goals, engineering capacity, and executive expectations collide.";
      case "engineering-management" -> "In engineering management, this often appears through one-on-ones, planning, feedback, hiring, delegation, and delivery health.";
      case "leadership" -> "In leadership, this often appears when people need direction, confidence, prioritization, or a decision they can repeat clearly.";
      case "cloud-computing" -> "In cloud work, this often appears around architecture choices, reliability, security, cost, migration risk, and operational ownership.";
      case "devops" -> "In DevOps, this often appears in deployment flow, incident response, automation, observability, and the friction between speed and safety.";
      case "cybersecurity" -> "In cybersecurity, this often appears when risk must be explained in a way that changes behavior without creating panic.";
      case "data-engineering" -> "In data engineering, this often appears when pipelines, definitions, freshness, quality, and trust affect business decisions.";
      case "artificial-intelligence" -> "In AI work, this often appears when tools, models, data, evaluation, and human judgment all have to be handled responsibly.";
      case "career-growth" -> "In career growth, this often appears when you need to turn ambition into evidence that managers, recruiters, and peers can understand.";
      case "interview-preparation" -> "In interview preparation, this often appears when you must translate real experience into clear stories, tradeoffs, and proof.";
      case "communication-skills" -> "In communication work, this often appears when progress depends less on having the right answer and more on creating shared understanding.";
      default -> "In daily work, this often appears when expectations are unclear and someone needs to create structure.";
    };
  }

  private String primarySkill(SeedArticle seed) {
    if (!seed.tags().isEmpty()) {
      return seed.tags().get(0).toLowerCase(Locale.US);
    }
    return seed.title().toLowerCase(Locale.US);
  }

  private String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private record SeedArticle(String title, String summary, List<String> tags, String author, String imageId,
      String intro, String practice, String measure) {}

  private Article article(String title, String slug, String summary, String category, List<String> tags, String author,
      ArticleStatus status, String publishedAt, long views, boolean featured, boolean editorPick, String image,
      String seoTitle, String seoDescription, String content) {
    Article article = new Article();
    article.setTitle(title);
    article.setSlug(slug);
    article.setSummary(summary);
    article.setCategory(category);
    article.setTags(tags);
    article.setAuthor(author);
    article.setStatus(status);
    article.setPublishedAt(publishedAt == null ? null : LocalDate.parse(publishedAt));
    article.setViews(views);
    article.setFeatured(featured);
    article.setEditorPick(editorPick);
    article.setImage(image);
    article.setSeoTitle(seoTitle);
    article.setSeoDescription(seoDescription);
    article.setContent(content);
    return article;
  }

  private void addSeedCategory(String id, String name, String description) {
    categories.add(new Category(id, name, description));
  }

  private String imageFor(String id) {
    return "https://images.unsplash.com/" + id + "?auto=format&fit=crop&w=1100&q=80";
  }
}
