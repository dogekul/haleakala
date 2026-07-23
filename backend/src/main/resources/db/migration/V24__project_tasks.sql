CREATE TABLE project_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  organization_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  title VARCHAR(240) NOT NULL,
  description TEXT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'TODO',
  priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
  creator_user_id BIGINT NOT NULL,
  assignee_user_id BIGINT NOT NULL,
  due_at TIMESTAMP(6) NULL,
  stage_code VARCHAR(32) NULL,
  milestone_id BIGINT NULL,
  completed_by_user_id BIGINT NULL,
  completed_at TIMESTAMP(6) NULL,
  version BIGINT NOT NULL DEFAULT 0,
  deleted BOOLEAN NOT NULL DEFAULT FALSE,
  deleted_by_user_id BIGINT NULL,
  deleted_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_task_org FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT fk_task_project FOREIGN KEY (project_id) REFERENCES delivery_project(id),
  CONSTRAINT fk_task_creator FOREIGN KEY (creator_user_id) REFERENCES app_user(id),
  CONSTRAINT fk_task_assignee FOREIGN KEY (assignee_user_id) REFERENCES app_user(id),
  CONSTRAINT fk_task_milestone FOREIGN KEY (milestone_id) REFERENCES milestone(id),
  CONSTRAINT fk_task_completer FOREIGN KEY (completed_by_user_id) REFERENCES app_user(id),
  CONSTRAINT fk_task_deleter FOREIGN KEY (deleted_by_user_id) REFERENCES app_user(id)
);

CREATE TABLE project_task_check_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  content VARCHAR(500) NOT NULL,
  completed BOOLEAN NOT NULL DEFAULT FALSE,
  sort_order INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_task_check_item_task FOREIGN KEY (task_id) REFERENCES project_task(id)
);

CREATE TABLE project_task_reminder (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  recipient_user_id BIGINT NOT NULL,
  channel VARCHAR(24) NOT NULL DEFAULT 'IN_APP',
  remind_at TIMESTAMP(6) NOT NULL,
  read_at TIMESTAMP(6) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  CONSTRAINT uk_task_reminder_channel UNIQUE (task_id, channel),
  CONSTRAINT fk_task_reminder_task FOREIGN KEY (task_id) REFERENCES project_task(id),
  CONSTRAINT fk_task_reminder_recipient FOREIGN KEY (recipient_user_id) REFERENCES app_user(id)
);

CREATE INDEX idx_project_task_project_status
  ON project_task(project_id, status, deleted);
CREATE INDEX idx_project_task_assignee_status
  ON project_task(assignee_user_id, status, deleted);
CREATE INDEX idx_project_task_due_status
  ON project_task(due_at, status, deleted);
CREATE INDEX idx_task_check_item_order
  ON project_task_check_item(task_id, sort_order);
CREATE INDEX idx_task_reminder_recipient
  ON project_task_reminder(recipient_user_id, read_at, remind_at);
