\set ON_ERROR_STOP on

SELECT validation.create_approval(
  :'approval_id',
  :'candidate_id',
  :'approval_state'::approval_state,
  :'reason'
);
