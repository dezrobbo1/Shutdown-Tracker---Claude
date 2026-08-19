-- Which Microsoft Project resource is which Shutdown Tracker user.
--
-- The field app has always listed every leaf task in the accepted snapshot, because nothing
-- connected a person to the work Project says is theirs. This is that connection, and the
-- shape it takes is decided by three rules the repository already holds.
--
-- **It is explicit, never inferred.** Matching a resource named "J. Okafor" to a user whose
-- display name is "Joseph Okafor" is a guess, and docs/product/project-operational-mapping.md
-- forbids activating an uncertain source identity without review. A link exists because
-- somebody with MANAGE_RESOURCE_LINK created it, and the audit event says who.
--
-- **It grants relevance, not permission.** AGENTS.md keeps visibility, responsibility, update
-- permission, review permission and export authority separate, and states that a
-- Project-derived membership is not application authorization. A link narrows what My Work
-- shows. What a person may then do is still resolved from project_memberships, and every
-- capability check is unchanged. Linking somebody to a resource can never widen their access,
-- and unlinking them can never take a permission away.
--
-- **It survives re-import.** The key is the resource's Project UID within the project, not the
-- imported_resources row, because that row is snapshot-scoped and a new snapshot replaces it.
-- A snapshot that no longer carries the resource leaves the link intact and unmatched rather
-- than deleting it, matching the rule that configuration for absent source values stays
-- available for historical records and for the value reappearing.

CREATE TABLE project_resource_links (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  project_id UUID NOT NULL REFERENCES projects(id),
  user_id UUID NOT NULL REFERENCES users(id),
  resource_external_uid TEXT NOT NULL,
  -- What the resource was called when the link was made. Not authority over the source, and
  -- never read back as the resource's name: it is here so a link to a resource the current
  -- snapshot has lost can still say what it once pointed at.
  resource_name_at_link TEXT,
  active BOOLEAN NOT NULL DEFAULT true,
  linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  linked_by_user_id UUID NOT NULL REFERENCES users(id),
  revoked_at TIMESTAMPTZ,
  revoked_by_user_id UUID REFERENCES users(id),
  CONSTRAINT project_resource_links_uid_not_blank_check
    CHECK (length(btrim(resource_external_uid)) > 0),
  CONSTRAINT project_resource_links_revoked_consistency_check CHECK (
    (active AND revoked_at IS NULL AND revoked_by_user_id IS NULL)
    OR (NOT active AND revoked_at IS NOT NULL)
  )
);

COMMENT ON TABLE project_resource_links IS
  'Explicit, reviewed link from a Microsoft Project resource to a Shutdown Tracker user. Grants relevance only; authorization stays with project_memberships.';
COMMENT ON COLUMN project_resource_links.resource_external_uid IS
  'The resource UID in the Project source. Project-scoped rather than snapshot-scoped, so the link survives re-import.';
COMMENT ON COLUMN project_resource_links.active IS
  'Revoking is a new state on the same row, not a delete, so the audit trail keeps who linked whom and when.';

-- One resource is one person. A person may hold several resources — a named resource and a
-- trade resource are both theirs — so the constraint is on the resource, not the user.
-- Partial, because a revoked link must not block linking that resource to somebody else.
CREATE UNIQUE INDEX idx_project_resource_links_active_resource
  ON project_resource_links (project_id, resource_external_uid)
  WHERE active;

-- Reading My Work resolves the acting user's resources first, so this is the hot path.
CREATE INDEX idx_project_resource_links_user
  ON project_resource_links (project_id, user_id)
  WHERE active;
