-- Chronos Education production schema baseline.
-- Generated from the verified 20/20 MVP schema; contains no business or demo data.
-- Existing installations ignore V0; new databases apply it before later migrations.

--
-- PostgreSQL database dump
--


-- Dumped from database version 17.10 (Debian 17.10-1.pgdg13+1)
-- Dumped by pg_dump version 17.10 (Debian 17.10-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: dblink; Type: EXTENSION; Schema: -; Owner: -
--



--
-- Name: EXTENSION dblink; Type: COMMENT; Schema: -; Owner: -
--



SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: act_evt_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_evt_log (
    log_nr_ integer NOT NULL,
    type_ character varying(64),
    proc_def_id_ character varying(64),
    proc_inst_id_ character varying(64),
    execution_id_ character varying(64),
    task_id_ character varying(64),
    time_stamp_ timestamp without time zone NOT NULL,
    user_id_ character varying(255),
    data_ bytea,
    lock_owner_ character varying(255),
    lock_time_ timestamp without time zone,
    is_processed_ smallint DEFAULT 0
);


--
-- Name: act_evt_log_log_nr__seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.act_evt_log_log_nr__seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: act_evt_log_log_nr__seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.act_evt_log_log_nr__seq OWNED BY public.act_evt_log.log_nr_;


--
-- Name: act_ge_bytearray; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ge_bytearray (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    name_ character varying(255),
    deployment_id_ character varying(64),
    bytes_ bytea,
    generated_ boolean
);


--
-- Name: act_ge_property; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ge_property (
    name_ character varying(64) NOT NULL,
    value_ character varying(300),
    rev_ integer
);


--
-- Name: act_hi_actinst; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_hi_actinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_def_id_ character varying(64) NOT NULL,
    proc_inst_id_ character varying(64) NOT NULL,
    execution_id_ character varying(64) NOT NULL,
    act_id_ character varying(255) NOT NULL,
    task_id_ character varying(64),
    call_proc_inst_id_ character varying(64),
    act_name_ character varying(255),
    act_type_ character varying(255) NOT NULL,
    assignee_ character varying(255),
    completed_by_ character varying(255),
    start_time_ timestamp without time zone NOT NULL,
    end_time_ timestamp without time zone,
    transaction_order_ integer,
    duration_ bigint,
    delete_reason_ character varying(4000),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);


--
-- Name: act_hi_attachment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_hi_attachment (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    user_id_ character varying(255),
    name_ character varying(255),
    description_ character varying(4000),
    type_ character varying(255),
    task_id_ character varying(64),
    proc_inst_id_ character varying(64),
    url_ character varying(4000),
    content_id_ character varying(64),
    time_ timestamp without time zone
);


--
-- Name: act_hi_comment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_hi_comment (
    id_ character varying(64) NOT NULL,
    type_ character varying(255),
    time_ timestamp without time zone NOT NULL,
    user_id_ character varying(255),
    task_id_ character varying(64),
    proc_inst_id_ character varying(64),
    action_ character varying(255),
    message_ character varying(4000),
    full_msg_ bytea
);


--
-- Name: act_hi_detail; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_hi_detail (
    id_ character varying(64) NOT NULL,
    type_ character varying(255) NOT NULL,
    proc_inst_id_ character varying(64),
    execution_id_ character varying(64),
    task_id_ character varying(64),
    act_inst_id_ character varying(64),
    name_ character varying(255) NOT NULL,
    var_type_ character varying(64),
    rev_ integer,
    time_ timestamp without time zone NOT NULL,
    bytearray_id_ character varying(64),
    double_ double precision,
    long_ bigint,
    text_ character varying(4000),
    text2_ character varying(4000)
);


--
-- Name: act_hi_entitylink; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_hi_entitylink (
    id_ character varying(64) NOT NULL,
    link_type_ character varying(255),
    create_time_ timestamp without time zone,
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    parent_element_id_ character varying(255),
    ref_scope_id_ character varying(255),
    ref_scope_type_ character varying(255),
    ref_scope_definition_id_ character varying(255),
    root_scope_id_ character varying(255),
    root_scope_type_ character varying(255),
    hierarchy_type_ character varying(255)
);


--
-- Name: act_hi_identitylink; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_hi_identitylink (
    id_ character varying(64) NOT NULL,
    group_id_ character varying(255),
    type_ character varying(255),
    user_id_ character varying(255),
    task_id_ character varying(64),
    create_time_ timestamp without time zone,
    proc_inst_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255)
);


--
-- Name: act_hi_procinst; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_hi_procinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_inst_id_ character varying(64) NOT NULL,
    business_key_ character varying(255),
    proc_def_id_ character varying(64) NOT NULL,
    start_time_ timestamp without time zone NOT NULL,
    end_time_ timestamp without time zone,
    duration_ bigint,
    start_user_id_ character varying(255),
    start_act_id_ character varying(255),
    end_act_id_ character varying(255),
    super_process_instance_id_ character varying(64),
    delete_reason_ character varying(4000),
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    name_ character varying(255),
    callback_id_ character varying(255),
    callback_type_ character varying(255),
    reference_id_ character varying(255),
    reference_type_ character varying(255),
    propagated_stage_inst_id_ character varying(255),
    business_status_ character varying(255),
    end_user_id_ character varying(255),
    state_ character varying(255)
);


--
-- Name: act_hi_taskinst; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_hi_taskinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_def_id_ character varying(64),
    task_def_id_ character varying(64),
    task_def_key_ character varying(255),
    proc_inst_id_ character varying(64),
    execution_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    propagated_stage_inst_id_ character varying(255),
    state_ character varying(255),
    name_ character varying(255),
    parent_task_id_ character varying(64),
    description_ character varying(4000),
    owner_ character varying(255),
    assignee_ character varying(255),
    start_time_ timestamp without time zone NOT NULL,
    in_progress_time_ timestamp without time zone,
    in_progress_started_by_ character varying(255),
    claim_time_ timestamp without time zone,
    claimed_by_ character varying(255),
    suspended_time_ timestamp without time zone,
    suspended_by_ character varying(255),
    end_time_ timestamp without time zone,
    completed_by_ character varying(255),
    duration_ bigint,
    delete_reason_ character varying(4000),
    priority_ integer,
    in_progress_due_date_ timestamp without time zone,
    due_date_ timestamp without time zone,
    form_key_ character varying(255),
    category_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    last_updated_time_ timestamp without time zone
);


--
-- Name: act_hi_tsk_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_hi_tsk_log (
    id_ integer NOT NULL,
    type_ character varying(64),
    task_id_ character varying(64) NOT NULL,
    time_stamp_ timestamp without time zone NOT NULL,
    user_id_ character varying(255),
    data_ character varying(4000),
    execution_id_ character varying(64),
    proc_inst_id_ character varying(64),
    proc_def_id_ character varying(64),
    scope_id_ character varying(255),
    scope_definition_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);


--
-- Name: act_hi_tsk_log_id__seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.act_hi_tsk_log_id__seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: act_hi_tsk_log_id__seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.act_hi_tsk_log_id__seq OWNED BY public.act_hi_tsk_log.id_;


--
-- Name: act_hi_varinst; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_hi_varinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_inst_id_ character varying(64),
    execution_id_ character varying(64),
    task_id_ character varying(64),
    name_ character varying(255) NOT NULL,
    var_type_ character varying(100),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    bytearray_id_ character varying(64),
    double_ double precision,
    long_ bigint,
    text_ character varying(4000),
    text2_ character varying(4000),
    meta_info_ character varying(4000),
    create_time_ timestamp without time zone,
    last_updated_time_ timestamp without time zone
);


--
-- Name: act_id_bytearray; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_id_bytearray (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    name_ character varying(255),
    bytes_ bytea
);


--
-- Name: act_id_group; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_id_group (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    name_ character varying(255),
    type_ character varying(255)
);


--
-- Name: act_id_info; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_id_info (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    user_id_ character varying(64),
    type_ character varying(64),
    key_ character varying(255),
    value_ character varying(255),
    password_ bytea,
    parent_id_ character varying(255)
);


--
-- Name: act_id_membership; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_id_membership (
    user_id_ character varying(64) NOT NULL,
    group_id_ character varying(64) NOT NULL
);


--
-- Name: act_id_priv; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_id_priv (
    id_ character varying(64) NOT NULL,
    name_ character varying(255) NOT NULL
);


--
-- Name: act_id_priv_mapping; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_id_priv_mapping (
    id_ character varying(64) NOT NULL,
    priv_id_ character varying(64) NOT NULL,
    user_id_ character varying(255),
    group_id_ character varying(255)
);


--
-- Name: act_id_property; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_id_property (
    name_ character varying(64) NOT NULL,
    value_ character varying(300),
    rev_ integer
);


--
-- Name: act_id_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_id_token (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    token_value_ character varying(255),
    token_date_ timestamp without time zone,
    ip_address_ character varying(255),
    user_agent_ character varying(255),
    user_id_ character varying(255),
    token_data_ character varying(2000)
);


--
-- Name: act_id_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_id_user (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    first_ character varying(255),
    last_ character varying(255),
    display_name_ character varying(255),
    email_ character varying(255),
    pwd_ character varying(255),
    picture_id_ character varying(64),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);


--
-- Name: act_procdef_info; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_procdef_info (
    id_ character varying(64) NOT NULL,
    proc_def_id_ character varying(64) NOT NULL,
    rev_ integer,
    info_json_id_ character varying(64)
);


--
-- Name: act_re_deployment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_re_deployment (
    id_ character varying(64) NOT NULL,
    name_ character varying(255),
    category_ character varying(255),
    key_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    deploy_time_ timestamp without time zone,
    derived_from_ character varying(64),
    derived_from_root_ character varying(64),
    parent_deployment_id_ character varying(255),
    engine_version_ character varying(255)
);


--
-- Name: act_re_model; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_re_model (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    name_ character varying(255),
    key_ character varying(255),
    category_ character varying(255),
    create_time_ timestamp without time zone,
    last_update_time_ timestamp without time zone,
    version_ integer,
    meta_info_ character varying(4000),
    deployment_id_ character varying(64),
    editor_source_value_id_ character varying(64),
    editor_source_extra_value_id_ character varying(64),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);


--
-- Name: act_re_procdef; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_re_procdef (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    name_ character varying(255),
    key_ character varying(255) NOT NULL,
    version_ integer NOT NULL,
    deployment_id_ character varying(64),
    resource_name_ character varying(4000),
    dgrm_resource_name_ character varying(4000),
    description_ character varying(4000),
    has_start_form_key_ boolean,
    has_graphical_notation_ boolean,
    suspension_state_ integer,
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    derived_from_ character varying(64),
    derived_from_root_ character varying(64),
    derived_version_ integer DEFAULT 0 NOT NULL,
    engine_version_ character varying(255)
);


--
-- Name: act_ru_actinst; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ru_actinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_def_id_ character varying(64) NOT NULL,
    proc_inst_id_ character varying(64) NOT NULL,
    execution_id_ character varying(64) NOT NULL,
    act_id_ character varying(255) NOT NULL,
    task_id_ character varying(64),
    call_proc_inst_id_ character varying(64),
    act_name_ character varying(255),
    act_type_ character varying(255) NOT NULL,
    assignee_ character varying(255),
    completed_by_ character varying(255),
    start_time_ timestamp without time zone NOT NULL,
    end_time_ timestamp without time zone,
    duration_ bigint,
    transaction_order_ integer,
    delete_reason_ character varying(4000),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);


--
-- Name: act_ru_deadletter_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ru_deadletter_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);


--
-- Name: act_ru_entitylink; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ru_entitylink (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    create_time_ timestamp without time zone,
    link_type_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    parent_element_id_ character varying(255),
    ref_scope_id_ character varying(255),
    ref_scope_type_ character varying(255),
    ref_scope_definition_id_ character varying(255),
    root_scope_id_ character varying(255),
    root_scope_type_ character varying(255),
    hierarchy_type_ character varying(255)
);


--
-- Name: act_ru_event_subscr; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ru_event_subscr (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    event_type_ character varying(255) NOT NULL,
    event_name_ character varying(255),
    execution_id_ character varying(64),
    proc_inst_id_ character varying(64),
    activity_id_ character varying(64),
    configuration_ character varying(255),
    created_ timestamp without time zone NOT NULL,
    proc_def_id_ character varying(64),
    sub_scope_id_ character varying(64),
    scope_id_ character varying(64),
    scope_definition_id_ character varying(64),
    scope_definition_key_ character varying(255),
    scope_type_ character varying(64),
    lock_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);


--
-- Name: act_ru_execution; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ru_execution (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    proc_inst_id_ character varying(64),
    business_key_ character varying(255),
    parent_id_ character varying(64),
    proc_def_id_ character varying(64),
    super_exec_ character varying(64),
    root_proc_inst_id_ character varying(64),
    act_id_ character varying(255),
    is_active_ boolean,
    is_concurrent_ boolean,
    is_scope_ boolean,
    is_event_scope_ boolean,
    is_mi_root_ boolean,
    suspension_state_ integer,
    cached_ent_state_ integer,
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    name_ character varying(255),
    start_act_id_ character varying(255),
    start_time_ timestamp without time zone,
    start_user_id_ character varying(255),
    lock_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    is_count_enabled_ boolean,
    evt_subscr_count_ integer,
    task_count_ integer,
    job_count_ integer,
    timer_job_count_ integer,
    susp_job_count_ integer,
    deadletter_job_count_ integer,
    external_worker_job_count_ integer,
    var_count_ integer,
    id_link_count_ integer,
    callback_id_ character varying(255),
    callback_type_ character varying(255),
    reference_id_ character varying(255),
    reference_type_ character varying(255),
    propagated_stage_inst_id_ character varying(255),
    business_status_ character varying(255)
);


--
-- Name: act_ru_external_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ru_external_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    lock_exp_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);


--
-- Name: act_ru_history_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ru_history_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    lock_exp_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    adv_handler_cfg_id_ character varying(64),
    create_time_ timestamp without time zone,
    scope_type_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);


--
-- Name: act_ru_identitylink; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ru_identitylink (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    group_id_ character varying(255),
    type_ character varying(255),
    user_id_ character varying(255),
    task_id_ character varying(64),
    proc_inst_id_ character varying(64),
    proc_def_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255)
);


--
-- Name: act_ru_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ru_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    lock_exp_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);


--
-- Name: act_ru_suspended_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ru_suspended_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);


--
-- Name: act_ru_task; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ru_task (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    execution_id_ character varying(64),
    proc_inst_id_ character varying(64),
    proc_def_id_ character varying(64),
    task_def_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    propagated_stage_inst_id_ character varying(255),
    state_ character varying(255),
    name_ character varying(255),
    parent_task_id_ character varying(64),
    description_ character varying(4000),
    task_def_key_ character varying(255),
    owner_ character varying(255),
    assignee_ character varying(255),
    delegation_ character varying(64),
    priority_ integer,
    create_time_ timestamp without time zone,
    in_progress_time_ timestamp without time zone,
    in_progress_started_by_ character varying(255),
    claim_time_ timestamp without time zone,
    claimed_by_ character varying(255),
    suspended_time_ timestamp without time zone,
    suspended_by_ character varying(255),
    in_progress_due_date_ timestamp without time zone,
    due_date_ timestamp without time zone,
    category_ character varying(255),
    suspension_state_ integer,
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    form_key_ character varying(255),
    is_count_enabled_ boolean,
    var_count_ integer,
    id_link_count_ integer,
    sub_task_count_ integer
);


--
-- Name: act_ru_timer_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ru_timer_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    lock_exp_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);


--
-- Name: act_ru_variable; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.act_ru_variable (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    type_ character varying(255) NOT NULL,
    name_ character varying(255) NOT NULL,
    execution_id_ character varying(64),
    proc_inst_id_ character varying(64),
    task_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    bytearray_id_ character varying(64),
    double_ double precision,
    long_ bigint,
    text_ character varying(4000),
    text2_ character varying(4000),
    meta_info_ character varying(4000)
);


--
-- Name: ai_model_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_model_config (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    adapter_class character varying(255),
    model_name character varying(128) NOT NULL,
    model_type character varying(64) NOT NULL,
    provider character varying(64) NOT NULL,
    signature_handler character varying(255),
    status integer NOT NULL,
    version character varying(64),
    api_key character varying(512),
    is_default boolean,
    base_url character varying(512),
    call_timeout_ms integer,
    connect_timeout_ms integer,
    max_tokens integer,
    read_timeout_ms integer,
    temperature double precision,
    top_p double precision,
    embedding_dimension integer,
    embedding_default boolean DEFAULT false NOT NULL,
    CONSTRAINT ai_model_config_call_timeout_ms_check CHECK (((call_timeout_ms >= 100) AND (call_timeout_ms <= 600000))),
    CONSTRAINT ai_model_config_connect_timeout_ms_check CHECK (((connect_timeout_ms <= 120000) AND (connect_timeout_ms >= 100))),
    CONSTRAINT ai_model_config_embedding_dimension_check CHECK (((embedding_dimension <= 32768) AND (embedding_dimension >= 1))),
    CONSTRAINT ai_model_config_max_tokens_check CHECK (((max_tokens <= 100000) AND (max_tokens >= 1))),
    CONSTRAINT ai_model_config_read_timeout_ms_check CHECK (((read_timeout_ms >= 100) AND (read_timeout_ms <= 600000))),
    CONSTRAINT ai_model_config_status_check CHECK (((status <= 1) AND (status >= 0)))
);


--
-- Name: d_capability; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.d_capability (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    active boolean NOT NULL,
    capability_category character varying(100),
    capability_code character varying(100) NOT NULL,
    capability_name character varying(100) NOT NULL,
    certification_required boolean NOT NULL,
    description oid,
    organization_id character varying(64) NOT NULL
);


--
-- Name: TABLE d_capability; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.d_capability IS '领域-资源能力';


--
-- Name: d_resource; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.d_resource (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    active boolean NOT NULL,
    calendar_ref character varying(128),
    cost_per_hour numeric(12,2),
    external_resource_id character varying(64),
    organization_id character varying(64) NOT NULL,
    organization_unit_id character varying(64),
    resource_code character varying(100) NOT NULL,
    resource_name character varying(100) NOT NULL,
    resource_status character varying(32) NOT NULL,
    resource_type character varying(32) NOT NULL,
    CONSTRAINT d_resource_resource_status_check CHECK (((resource_status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'MAINTENANCE'::character varying, 'RETIRED'::character varying])::text[]))),
    CONSTRAINT d_resource_resource_type_check CHECK (((resource_type)::text = ANY ((ARRAY['PERSON'::character varying, 'EQUIPMENT'::character varying, 'ROOM'::character varying, 'VEHICLE'::character varying, 'OTHER'::character varying])::text[])))
);


--
-- Name: TABLE d_resource; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.d_resource IS '领域-可排程资源';


--
-- Name: d_resource_capability; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.d_resource_capability (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    capability_level character varying(16) NOT NULL,
    effective_from date,
    effective_to date,
    score integer,
    capability_id character varying(64) NOT NULL,
    resource_id character varying(64) NOT NULL,
    CONSTRAINT d_resource_capability_capability_level_check CHECK (((capability_level)::text = ANY ((ARRAY['L1'::character varying, 'L2'::character varying, 'L3'::character varying, 'L4'::character varying, 'L5'::character varying])::text[])))
);


--
-- Name: TABLE d_resource_capability; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.d_resource_capability IS '领域-资源能力绑定';


--
-- Name: d_schedule_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.d_schedule_assignment (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    end_time timestamp(6) without time zone NOT NULL,
    locked boolean NOT NULL,
    organization_unit_id character varying(64),
    start_time timestamp(6) without time zone NOT NULL,
    violation_tags character varying(500),
    resource_id character varying(64) NOT NULL,
    schedule_result_id character varying(64) NOT NULL,
    task_id character varying(64) NOT NULL
);


--
-- Name: TABLE d_schedule_assignment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.d_schedule_assignment IS '领域-排程明细';


--
-- Name: d_schedule_result; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.d_schedule_result (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    finished_at timestamp(6) without time zone,
    hard_score numeric(20,4),
    input_snapshot_json oid,
    objective_score numeric(20,4),
    organization_id character varying(64) NOT NULL,
    planning_end_time timestamp(6) without time zone NOT NULL,
    planning_start_time timestamp(6) without time zone NOT NULL,
    rule_snapshot_json oid,
    scenario character varying(120),
    schedule_code character varying(100) NOT NULL,
    schedule_name character varying(120) NOT NULL,
    schedule_status character varying(16) NOT NULL,
    soft_score numeric(20,4),
    started_at timestamp(6) without time zone,
    summary_json oid,
    CONSTRAINT d_schedule_result_schedule_status_check CHECK (((schedule_status)::text = ANY ((ARRAY['CREATED'::character varying, 'RUNNING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying, 'PUBLISHED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: TABLE d_schedule_result; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.d_schedule_result IS '领域-排程结果';


--
-- Name: d_schedule_rule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.d_schedule_rule (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    description character varying(500),
    enabled boolean NOT NULL,
    organization_id character varying(64) NOT NULL,
    organization_unit_id character varying(64),
    rule_code character varying(100) NOT NULL,
    rule_engine character varying(50),
    rule_expression oid NOT NULL,
    rule_name character varying(120) NOT NULL,
    rule_scope character varying(32) NOT NULL,
    rule_type character varying(16) NOT NULL,
    weight integer,
    CONSTRAINT d_schedule_rule_rule_scope_check CHECK (((rule_scope)::text = ANY ((ARRAY['GLOBAL'::character varying, 'ORGANIZATION_UNIT'::character varying, 'RESOURCE'::character varying, 'TASK'::character varying])::text[]))),
    CONSTRAINT d_schedule_rule_rule_type_check CHECK (((rule_type)::text = ANY ((ARRAY['HARD'::character varying, 'SOFT'::character varying])::text[])))
);


--
-- Name: TABLE d_schedule_rule; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.d_schedule_rule IS '领域-排程规则';


--
-- Name: d_task; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.d_task (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    description character varying(500),
    duration_minutes integer NOT NULL,
    earliest_start_time timestamp(6) without time zone,
    latest_end_time timestamp(6) without time zone,
    organization_id character varying(64) NOT NULL,
    organization_unit_id character varying(64),
    preemptive boolean NOT NULL,
    priority character varying(16) NOT NULL,
    required_count integer NOT NULL,
    required_resource_type character varying(32) NOT NULL,
    task_code character varying(100) NOT NULL,
    task_name character varying(200) NOT NULL,
    task_status character varying(16) NOT NULL,
    CONSTRAINT d_task_priority_check CHECK (((priority)::text = ANY ((ARRAY['LOW'::character varying, 'MEDIUM'::character varying, 'HIGH'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT d_task_required_resource_type_check CHECK (((required_resource_type)::text = ANY ((ARRAY['PERSON'::character varying, 'EQUIPMENT'::character varying, 'ROOM'::character varying, 'VEHICLE'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT d_task_task_status_check CHECK (((task_status)::text = ANY ((ARRAY['DRAFT'::character varying, 'READY'::character varying, 'SCHEDULED'::character varying, 'RUNNING'::character varying, 'DONE'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: TABLE d_task; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.d_task IS '领域-任务';


--
-- Name: d_task_capability_requirement; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.d_task_capability_requirement (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    minimum_level character varying(16) NOT NULL,
    required_count integer NOT NULL,
    capability_id character varying(64) NOT NULL,
    task_id character varying(64) NOT NULL,
    CONSTRAINT d_task_capability_requirement_minimum_level_check CHECK (((minimum_level)::text = ANY ((ARRAY['L1'::character varying, 'L2'::character varying, 'L3'::character varying, 'L4'::character varying, 'L5'::character varying])::text[])))
);


--
-- Name: TABLE d_task_capability_requirement; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.d_task_capability_requirement IS '领域-任务能力需求';


--
-- Name: edu_academic_term; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_academic_term (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    academic_year character varying(16) NOT NULL,
    current_term boolean NOT NULL,
    end_date date NOT NULL,
    start_date date NOT NULL,
    status character varying(24) NOT NULL,
    term_code character varying(32) NOT NULL,
    term_name character varying(128) NOT NULL,
    term_no integer NOT NULL,
    week_count integer NOT NULL
);


--
-- Name: edu_administrative_class; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_administrative_class (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    campus_id character varying(64),
    class_code character varying(64) NOT NULL,
    class_name character varying(128) NOT NULL,
    grade_year integer NOT NULL,
    head_teacher_id character varying(64),
    major_id character varying(64) NOT NULL,
    status character varying(24) NOT NULL,
    grade_id character varying(64)
);


--
-- Name: edu_classroom; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_classroom (
    id character varying(64) NOT NULL,
    room_code character varying(64) NOT NULL,
    room_name character varying(128) NOT NULL,
    campus_id character varying(64),
    building_name character varying(128),
    capacity integer DEFAULT 0 NOT NULL,
    room_type character varying(32) DEFAULT 'STANDARD'::character varying NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp without time zone,
    CONSTRAINT ck_edu_classroom_capacity CHECK ((capacity > 0))
);


--
-- Name: edu_course_adjustment_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_course_adjustment_record (
    id character varying(64) NOT NULL,
    create_by character varying(128),
    create_time timestamp without time zone,
    last_update_by character varying(128),
    last_update_time timestamp without time zone,
    lock_version bigint DEFAULT 0,
    workflow_instance_id character varying(64) NOT NULL,
    business_key character varying(128),
    schedule_entry_id character varying(64) NOT NULL,
    adjustment_type character varying(24) NOT NULL,
    result_entry_id character varying(64),
    status character varying(24) NOT NULL,
    message character varying(1000),
    request_payload text,
    retry_count integer DEFAULT 0 NOT NULL,
    last_retry_by character varying(100)
);


--
-- Name: edu_course_catalog; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_course_catalog (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    course_category character varying(32) NOT NULL,
    course_code character varying(64) NOT NULL,
    course_name character varying(128) NOT NULL,
    course_nature character varying(32) NOT NULL,
    credits numeric(6,2),
    enabled boolean NOT NULL,
    practice_hours integer NOT NULL,
    required_room_type character varying(32),
    theory_hours integer NOT NULL,
    total_hours integer NOT NULL,
    subject_id character varying(64)
);


--
-- Name: edu_course_offering; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_course_offering (
    id character varying(64) NOT NULL,
    semester_code character varying(32) NOT NULL,
    offering_code character varying(64) NOT NULL,
    course_code character varying(64) NOT NULL,
    course_name character varying(128) NOT NULL,
    teaching_class_name character varying(128) NOT NULL,
    teacher_id character varying(64) NOT NULL,
    teacher_name character varying(128) NOT NULL,
    student_count integer DEFAULT 0 NOT NULL,
    weekly_lessons integer DEFAULT 2 NOT NULL,
    campus_id character varying(64),
    status character varying(24) DEFAULT 'ACTIVE'::character varying NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp without time zone,
    CONSTRAINT ck_edu_offering_lessons CHECK ((weekly_lessons > 0)),
    CONSTRAINT ck_edu_offering_students CHECK ((student_count > 0))
);


--
-- Name: edu_grade; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_grade (
    id character varying(64) NOT NULL,
    grade_code character varying(64) NOT NULL,
    grade_name character varying(128) NOT NULL,
    enrollment_year integer NOT NULL,
    education_stage character varying(32) NOT NULL,
    director_teacher_id character varying(64),
    enabled boolean DEFAULT true NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp without time zone
);


--
-- Name: edu_leave_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_leave_request (
    id character varying(64) NOT NULL,
    create_by character varying(128),
    create_time timestamp without time zone,
    last_update_by character varying(128),
    last_update_time timestamp without time zone,
    lock_version bigint DEFAULT 0,
    workflow_instance_id character varying(64) NOT NULL,
    business_key character varying(128),
    applicant_type character varying(16) NOT NULL,
    applicant_id character varying(64) NOT NULL,
    leave_type character varying(32) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    reason character varying(1000) NOT NULL,
    status character varying(24) NOT NULL,
    approved_by character varying(128)
);


--
-- Name: edu_major; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_major (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    department_id character varying(64),
    enabled boolean NOT NULL,
    major_code character varying(64) NOT NULL,
    major_name character varying(128) NOT NULL,
    schooling_years integer NOT NULL
);


--
-- Name: edu_parent_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_parent_profile (
    id character varying(64) NOT NULL,
    parent_no character varying(64) NOT NULL,
    parent_name character varying(128) NOT NULL,
    gender character varying(16),
    phone character varying(32) NOT NULL,
    employment character varying(128),
    status character varying(24) DEFAULT 'ACTIVE'::character varying NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp without time zone
);


--
-- Name: edu_schedule_candidate_plan; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_schedule_candidate_plan (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp without time zone,
    lock_version bigint DEFAULT 0 NOT NULL,
    semester_code character varying(32) NOT NULL,
    plan_name character varying(128) NOT NULL,
    generation_mode character varying(16) NOT NULL,
    scope_json text NOT NULL,
    baseline_hash character varying(64) NOT NULL,
    snapshot_json text NOT NULL,
    metrics_json text NOT NULL,
    entry_count integer NOT NULL,
    unscheduled_lessons integer NOT NULL,
    total_score integer NOT NULL,
    status character varying(24) DEFAULT 'CANDIDATE'::character varying NOT NULL,
    generated_by character varying(128) NOT NULL,
    generated_at timestamp without time zone NOT NULL,
    applied_by character varying(128),
    applied_at timestamp without time zone,
    CONSTRAINT ck_edu_candidate_mode CHECK (((generation_mode)::text = ANY ((ARRAY['FULL'::character varying, 'LOCAL'::character varying])::text[]))),
    CONSTRAINT ck_edu_candidate_status CHECK (((status)::text = ANY ((ARRAY['CANDIDATE'::character varying, 'APPLIED'::character varying, 'DISCARDED'::character varying])::text[]))),
    CONSTRAINT ck_edu_candidate_unscheduled CHECK ((unscheduled_lessons >= 0))
);


--
-- Name: edu_schedule_entry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_schedule_entry (
    id character varying(64) NOT NULL,
    semester_code character varying(32) NOT NULL,
    offering_id character varying(64) NOT NULL,
    classroom_id character varying(64) NOT NULL,
    day_of_week integer NOT NULL,
    period_no integer NOT NULL,
    start_week integer DEFAULT 1 NOT NULL,
    end_week integer DEFAULT 20 NOT NULL,
    status character varying(24) DEFAULT 'SCHEDULED'::character varying NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp without time zone,
    duration_periods integer DEFAULT 1 NOT NULL,
    locked boolean DEFAULT false NOT NULL,
    week_pattern character varying(16) DEFAULT 'ALL'::character varying NOT NULL,
    substitute_teacher_id character varying(64),
    source_adjustment_instance_id character varying(64),
    CONSTRAINT ck_edu_schedule_day CHECK (((day_of_week >= 1) AND (day_of_week <= 7))),
    CONSTRAINT ck_edu_schedule_period CHECK ((period_no > 0)),
    CONSTRAINT ck_edu_schedule_weeks CHECK (((start_week > 0) AND (end_week >= start_week)))
);


--
-- Name: edu_schedule_plan_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_schedule_plan_version (
    id character varying(64) NOT NULL,
    create_by character varying(128),
    create_time timestamp without time zone,
    last_update_by character varying(128),
    last_update_time timestamp without time zone,
    lock_version bigint DEFAULT 0,
    semester_code character varying(32) NOT NULL,
    version_no integer NOT NULL,
    status character varying(24) DEFAULT 'PUBLISHED'::character varying NOT NULL,
    source_version_no integer,
    entry_count integer NOT NULL,
    snapshot_json text NOT NULL,
    published_by character varying(128) NOT NULL,
    published_at timestamp without time zone NOT NULL,
    CONSTRAINT ck_edu_schedule_entry_count CHECK ((entry_count >= 0)),
    CONSTRAINT ck_edu_schedule_version_no CHECK ((version_no > 0))
);


--
-- Name: edu_scheduling_agent_proposal; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_scheduling_agent_proposal (
    id character varying(64) NOT NULL,
    semester_code character varying(32) NOT NULL,
    request_text text NOT NULL,
    teacher_id character varying(64) NOT NULL,
    teacher_name character varying(128) NOT NULL,
    day_of_week integer NOT NULL,
    period_no integer NOT NULL,
    constraint_type character varying(24) NOT NULL,
    reason character varying(500),
    status character varying(24) DEFAULT 'DRAFT'::character varying NOT NULL,
    confirmed_by character varying(128),
    confirmed_at timestamp without time zone,
    applied_constraint_id character varying(64),
    create_by character varying(128) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp without time zone,
    CONSTRAINT ck_agent_proposal_day CHECK (((day_of_week >= 1) AND (day_of_week <= 7))),
    CONSTRAINT ck_agent_proposal_period CHECK ((period_no > 0)),
    CONSTRAINT ck_agent_proposal_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CONFIRMED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT ck_agent_proposal_type CHECK (((constraint_type)::text = ANY ((ARRAY['FORBIDDEN'::character varying, 'PREFERRED'::character varying])::text[])))
);


--
-- Name: edu_student_guardian; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_student_guardian (
    id character varying(64) NOT NULL,
    student_id character varying(64) NOT NULL,
    parent_id character varying(64) NOT NULL,
    relationship character varying(32) NOT NULL,
    primary_guardian boolean DEFAULT false NOT NULL,
    emergency_contact boolean DEFAULT false NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp without time zone
);


--
-- Name: edu_student_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_student_profile (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    administrative_class_id character varying(64) NOT NULL,
    enrollment_status character varying(24) NOT NULL,
    gender character varying(16),
    grade_year integer NOT NULL,
    major_id character varying(64) NOT NULL,
    phone character varying(32),
    student_name character varying(128) NOT NULL,
    student_no character varying(64) NOT NULL,
    grade_id character varying(64)
);


--
-- Name: edu_subject; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_subject (
    id character varying(64) NOT NULL,
    subject_code character varying(64) NOT NULL,
    subject_name character varying(128) NOT NULL,
    subject_category character varying(32) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp without time zone
);


--
-- Name: edu_teacher_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_teacher_profile (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    department_id character varying(64),
    employee_id character varying(64) NOT NULL,
    enabled boolean NOT NULL,
    max_weekly_lessons integer NOT NULL,
    specialty character varying(128),
    teacher_name character varying(128) NOT NULL,
    teacher_no character varying(64) NOT NULL
);


--
-- Name: edu_teacher_teaching_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_teacher_teaching_assignment (
    id character varying(64) NOT NULL,
    academic_term_id character varying(64) NOT NULL,
    teacher_id character varying(64) NOT NULL,
    subject_id character varying(64) NOT NULL,
    grade_id character varying(64),
    administrative_class_id character varying(64),
    assignment_role character varying(32) DEFAULT 'TEACHER'::character varying NOT NULL,
    weekly_lessons integer DEFAULT 0 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp without time zone,
    CONSTRAINT ck_edu_assignment_weekly_lessons CHECK ((weekly_lessons >= 0))
);


--
-- Name: edu_teacher_time_constraint; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_teacher_time_constraint (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    constraint_type character varying(24) NOT NULL,
    day_of_week integer NOT NULL,
    period_no integer NOT NULL,
    reason character varying(500),
    semester_code character varying(32) NOT NULL,
    teacher_id character varying(64) NOT NULL,
    weight integer NOT NULL
);


--
-- Name: edu_teaching_class_member; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_teaching_class_member (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    enrolled_at timestamp(6) without time zone NOT NULL,
    enrollment_status character varying(24) NOT NULL,
    offering_id character varying(64) NOT NULL,
    student_id character varying(64) NOT NULL,
    withdrawn_at timestamp(6) without time zone
);


--
-- Name: edu_user_profile_binding; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.edu_user_profile_binding (
    id character varying(64) NOT NULL,
    create_by character varying(128),
    create_time timestamp without time zone,
    last_update_by character varying(128),
    last_update_time timestamp without time zone,
    username character varying(100) NOT NULL,
    profile_type character varying(24) NOT NULL,
    profile_id character varying(64) NOT NULL,
    status character varying(24) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT ck_edu_user_profile_type CHECK (((profile_type)::text = ANY ((ARRAY['TEACHER'::character varying, 'STUDENT'::character varying, 'PARENT'::character varying])::text[])))
);


--
-- Name: flw_channel_definition; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flw_channel_definition (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    version_ integer,
    key_ character varying(255),
    category_ character varying(255),
    type_ character varying(255),
    implementation_ character varying(255),
    deployment_id_ character varying(255),
    create_time_ timestamp(3) without time zone,
    tenant_id_ character varying(255),
    resource_name_ character varying(255),
    description_ character varying(255)
);


--
-- Name: flw_event_definition; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flw_event_definition (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    version_ integer,
    key_ character varying(255),
    category_ character varying(255),
    deployment_id_ character varying(255),
    tenant_id_ character varying(255),
    resource_name_ character varying(255),
    description_ character varying(255)
);


--
-- Name: flw_event_deployment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flw_event_deployment (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    category_ character varying(255),
    deploy_time_ timestamp(3) without time zone,
    tenant_id_ character varying(255),
    parent_deployment_id_ character varying(255)
);


--
-- Name: flw_event_resource; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flw_event_resource (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    deployment_id_ character varying(255),
    resource_bytes_ bytea
);


--
-- Name: flw_ru_batch; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flw_ru_batch (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    type_ character varying(64) NOT NULL,
    search_key_ character varying(255),
    search_key2_ character varying(255),
    create_time_ timestamp without time zone NOT NULL,
    complete_time_ timestamp without time zone,
    status_ character varying(255),
    batch_doc_id_ character varying(64),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);


--
-- Name: flw_ru_batch_part; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flw_ru_batch_part (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    batch_id_ character varying(64),
    type_ character varying(64) NOT NULL,
    scope_id_ character varying(64),
    sub_scope_id_ character varying(64),
    scope_type_ character varying(64),
    search_key_ character varying(255),
    search_key2_ character varying(255),
    create_time_ timestamp without time zone NOT NULL,
    complete_time_ timestamp without time zone,
    status_ character varying(255),
    result_doc_id_ character varying(64),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);


--
-- Name: form_definition; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.form_definition (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    description character varying(500),
    form_key character varying(100) NOT NULL,
    form_name character varying(160) NOT NULL,
    published_at timestamp(6) without time zone,
    status character varying(30) NOT NULL,
    version character varying(40) NOT NULL
);


--
-- Name: form_field; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.form_field (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    field_key character varying(100) NOT NULL,
    field_label character varying(160) NOT NULL,
    field_type character varying(40) NOT NULL,
    form_id character varying(64) NOT NULL,
    options_json oid,
    required boolean NOT NULL,
    sort_order integer NOT NULL
);


--
-- Name: form_instance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.form_instance (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    data_json oid NOT NULL,
    form_id character varying(64) NOT NULL,
    form_role character varying(30) NOT NULL,
    node_key character varying(100) NOT NULL,
    owner character varying(128) NOT NULL,
    status character varying(30) NOT NULL,
    workflow_instance_id character varying(64) NOT NULL
);


--
-- Name: kb_document; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_document (
    id character varying(64) NOT NULL,
    knowledge_base_id character varying(64) NOT NULL,
    title character varying(256) NOT NULL,
    source_type character varying(32) NOT NULL,
    original_filename character varying(512),
    content text NOT NULL,
    chunk_count integer DEFAULT 0 NOT NULL,
    status character varying(24) DEFAULT 'READY'::character varying NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp without time zone,
    CONSTRAINT ck_kb_document_chunk_count CHECK ((chunk_count >= 0))
);


--
-- Name: kb_document_chunk; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_document_chunk (
    id character varying(64) NOT NULL,
    document_id character varying(64) NOT NULL,
    chunk_index integer NOT NULL,
    content text NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp without time zone,
    embedding_indexed boolean DEFAULT false NOT NULL,
    CONSTRAINT ck_kb_chunk_index CHECK ((chunk_index > 0))
);


--
-- Name: kb_knowledge_base; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.kb_knowledge_base (
    id character varying(64) NOT NULL,
    base_code character varying(64) NOT NULL,
    base_name character varying(128) NOT NULL,
    description text,
    organization_id character varying(64),
    enabled boolean DEFAULT true NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp without time zone,
    answer_model_id character varying(64),
    embedding_model_id character varying(64),
    index_error text,
    retrieval_mode character varying(16) DEFAULT 'KEYWORD'::character varying NOT NULL,
    allow_keyword_fallback boolean DEFAULT true NOT NULL,
    index_status character varying(24) DEFAULT 'NOT_INDEXED'::character varying NOT NULL
);


--
-- Name: msg_channel_policy; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.msg_channel_policy (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    channel character varying(32) NOT NULL,
    enabled boolean NOT NULL,
    max_per_day integer NOT NULL,
    max_per_minute integer NOT NULL,
    min_interval_seconds integer NOT NULL
);


--
-- Name: msg_channel_preference; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.msg_channel_preference (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    channel character varying(32) NOT NULL,
    daily_limit integer,
    enabled boolean NOT NULL,
    quiet_end time(0) without time zone,
    quiet_start time(0) without time zone,
    username character varying(128) NOT NULL
);


--
-- Name: msg_notification_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.msg_notification_template (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    channel character varying(32) NOT NULL,
    content_template text NOT NULL,
    enabled boolean NOT NULL,
    subject_template character varying(500),
    template_code character varying(100) NOT NULL,
    template_name character varying(200) NOT NULL
);


--
-- Name: msg_publication; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.msg_publication (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    approval_instance_id character varying(64),
    approval_required boolean DEFAULT false NOT NULL,
    approval_status character varying(32),
    approval_workflow_definition_id character varying(64),
    audience_mode character varying(16) DEFAULT 'SNAPSHOT'::character varying NOT NULL,
    content text,
    content_type character varying(32) NOT NULL,
    expire_at timestamp(6) without time zone,
    importance character varying(16) NOT NULL,
    lock_version bigint DEFAULT 0 NOT NULL,
    must_read boolean NOT NULL,
    owner_organization_id character varying(64),
    pinned boolean NOT NULL,
    publication_type character varying(32) NOT NULL,
    publish_at timestamp(6) without time zone,
    published_at timestamp(6) without time zone,
    read_deadline timestamp(6) without time zone,
    review_comment character varying(1000),
    reviewed_at timestamp(6) without time zone,
    reviewed_by character varying(128),
    sort_order integer NOT NULL,
    status character varying(32) NOT NULL,
    submitted_at timestamp(6) without time zone,
    submitted_by character varying(128),
    summary character varying(1000),
    title character varying(300) NOT NULL,
    version_no integer NOT NULL,
    withdrawn_at timestamp(6) without time zone,
    withdrawn_by character varying(128),
    archived boolean DEFAULT false NOT NULL,
    archived_at timestamp(6) without time zone,
    archived_by character varying(128)
);


--
-- Name: TABLE msg_publication; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.msg_publication IS '通知公告发布主体';


--
-- Name: msg_publication_attachment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.msg_publication_attachment (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    content_type character varying(200),
    file_size bigint NOT NULL,
    original_name character varying(500) NOT NULL,
    primary_content boolean NOT NULL,
    publication_id character varying(64) NOT NULL,
    sha256 character varying(64) NOT NULL,
    storage_key character varying(500) NOT NULL
);


--
-- Name: TABLE msg_publication_attachment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.msg_publication_attachment IS '通知公告附件';


--
-- Name: msg_publication_audience; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.msg_publication_audience (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    excluded boolean NOT NULL,
    include_children boolean NOT NULL,
    publication_id character varying(64) NOT NULL,
    subject_id character varying(128) NOT NULL,
    subject_type character varying(32) NOT NULL
);


--
-- Name: TABLE msg_publication_audience; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.msg_publication_audience IS '通知公告受众';


--
-- Name: msg_publication_delivery; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.msg_publication_delivery (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    attempt_count integer NOT NULL,
    channel character varying(32) NOT NULL,
    delivered_at timestamp(6) without time zone,
    last_error character varying(1000),
    next_attempt_at timestamp(6) without time zone,
    publication_id character varying(64) NOT NULL,
    status character varying(32) NOT NULL,
    username character varying(128) NOT NULL
);


--
-- Name: TABLE msg_publication_delivery; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.msg_publication_delivery IS '通知公告触达任务';


--
-- Name: msg_publication_read; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.msg_publication_read (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    publication_id character varying(64) NOT NULL,
    read_at timestamp(6) without time zone NOT NULL,
    username character varying(128) NOT NULL
);


--
-- Name: TABLE msg_publication_read; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.msg_publication_read IS '通知公告阅读回执';


--
-- Name: msg_publication_recipient; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.msg_publication_recipient (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    publication_id character varying(64) NOT NULL,
    username character varying(128) NOT NULL
);


--
-- Name: TABLE msg_publication_recipient; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.msg_publication_recipient IS '通知公告收件人快照';


--
-- Name: msg_publication_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.msg_publication_version (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    operation character varying(32) NOT NULL,
    publication_id character varying(64) NOT NULL,
    snapshot_json text NOT NULL,
    version_no integer NOT NULL
);


--
-- Name: TABLE msg_publication_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.msg_publication_version IS '通知公告历史版本';


--
-- Name: sys_industry_data_sync_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_industry_data_sync_log (
    id bigint NOT NULL,
    source_database character varying(64) NOT NULL,
    target_database character varying(64) NOT NULL,
    sync_scope character varying(128) NOT NULL,
    sync_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    details jsonb NOT NULL
);


--
-- Name: sys_industry_data_sync_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sys_industry_data_sync_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sys_industry_data_sync_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sys_industry_data_sync_log_id_seq OWNED BY public.sys_industry_data_sync_log.id;


--
-- Name: sys_industry_template_installation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sys_industry_template_installation (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    active boolean NOT NULL,
    industry_code character varying(32) NOT NULL,
    installed_at timestamp(6) without time zone NOT NULL,
    installed_version character varying(32) NOT NULL
);


--
-- Name: t_admin_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_admin_user (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    account_locked boolean,
    account_type character varying(32),
    avatar_url character varying(500),
    display_name character varying(100),
    email character varying(200),
    employee_id character varying(64),
    failed_login_attempts integer,
    last_login_at timestamp(6) without time zone,
    last_login_ip character varying(64),
    lock_until timestamp(6) without time zone,
    must_change_password boolean,
    organization_id character varying(64),
    password character varying(200) NOT NULL,
    password_changed_at timestamp(6) without time zone,
    phone character varying(32),
    position_name character varying(100),
    status integer NOT NULL,
    token_version integer,
    username character varying(100) NOT NULL
);


--
-- Name: TABLE t_admin_user; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_admin_user IS '后台用户表';


--
-- Name: t_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_audit_log (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    action character varying(200),
    detail character varying(2000),
    username character varying(200)
);


--
-- Name: TABLE t_audit_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_audit_log IS 'Audit log';


--
-- Name: t_consumer_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_consumer_user (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    customer_type character varying(50) NOT NULL,
    email character varying(200),
    password character varying(200) NOT NULL,
    phone character varying(32),
    status integer NOT NULL,
    username character varying(100) NOT NULL
);


--
-- Name: TABLE t_consumer_user; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_consumer_user IS '用户表';


--
-- Name: t_dict; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_dict (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    dict_code character varying(100) NOT NULL,
    dict_name character varying(200) NOT NULL,
    dict_value character varying(200),
    parent_id character varying(64),
    status integer NOT NULL
);


--
-- Name: TABLE t_dict; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_dict IS '字典表';


--
-- Name: t_employee_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_employee_assignment (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    department_leader boolean NOT NULL,
    effective_from date,
    effective_to date,
    employee_id character varying(64) NOT NULL,
    job_level_id character varying(64),
    job_title_id character varying(64),
    organization_id character varying(64) NOT NULL,
    organization_unit_id character varying(64) NOT NULL,
    position_id character varying(64),
    primary_assignment boolean NOT NULL,
    status integer NOT NULL
);


--
-- Name: TABLE t_employee_assignment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_employee_assignment IS '员工任职关系';


--
-- Name: t_iam_employee; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_iam_employee (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    email character varying(200),
    employee_code character varying(100) NOT NULL,
    employee_name character varying(100) NOT NULL,
    employee_type character varying(32) NOT NULL,
    employment_status character varying(32) NOT NULL,
    gender character varying(16),
    hire_date date,
    id_number_cipher character varying(500),
    leave_date date,
    phone character varying(32)
);


--
-- Name: TABLE t_iam_employee; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_iam_employee IS 'IAM员工档案';


--
-- Name: t_job_level; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_job_level (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    description character varying(500),
    level_category character varying(50),
    level_code character varying(100) NOT NULL,
    level_name character varying(100) NOT NULL,
    level_sequence integer NOT NULL,
    sort_order integer NOT NULL,
    status integer NOT NULL
);


--
-- Name: TABLE t_job_level; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_job_level IS '员工职级';


--
-- Name: t_job_title; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_job_title (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    sort_order integer NOT NULL,
    status integer NOT NULL,
    title_code character varying(100) NOT NULL,
    title_level character varying(50),
    title_name character varying(100) NOT NULL,
    title_type character varying(32) NOT NULL
);


--
-- Name: TABLE t_job_title; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_job_title IS '职务职称';


--
-- Name: t_managed_file; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_managed_file (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp without time zone,
    original_name character varying(255) NOT NULL,
    storage_key character varying(500) NOT NULL,
    content_type character varying(128) NOT NULL,
    file_size bigint NOT NULL,
    sha256 character varying(64) NOT NULL,
    owner_username character varying(100) NOT NULL,
    business_type character varying(64) NOT NULL,
    business_id character varying(128),
    status character varying(24) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT ck_managed_file_size CHECK ((file_size > 0)),
    CONSTRAINT ck_managed_file_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DELETED'::character varying])::text[])))
);


--
-- Name: t_menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_menu (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    menu_name character varying(200) NOT NULL,
    order_num integer,
    parent_id character varying(64),
    path character varying(500)
);


--
-- Name: TABLE t_menu; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_menu IS '菜单表';


--
-- Name: t_organization; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_organization (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    description character varying(500),
    industries character varying(500),
    mailing_address character varying(500),
    org_code character varying(100) NOT NULL,
    managerid character varying(100),
    organization_name character varying(200) NOT NULL,
    organization_type character varying(32),
    register_time timestamp(6) without time zone NOT NULL,
    short_name character varying(100),
    sort_order integer,
    status integer,
    tel character varying(32),
    timezone character varying(64),
    parent_org_id character varying(64)
);


--
-- Name: TABLE t_organization; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_organization IS '机构表';


--
-- Name: t_organization_unit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_organization_unit (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    description character varying(500),
    leader_employee_id character varying(64),
    level integer NOT NULL,
    org_id character varying(64) NOT NULL,
    organization_unit_code character varying(100) NOT NULL,
    organization_unit_name character varying(100) NOT NULL,
    sort_order integer,
    status integer,
    tree_path character varying(1000),
    unit_type character varying(32),
    parent_organization_unit_id character varying(64)
);


--
-- Name: TABLE t_organization_unit; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_organization_unit IS '组织单元表';


--
-- Name: t_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_permission (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    action_type character varying(32),
    built_in boolean DEFAULT false NOT NULL,
    config_json text,
    description character varying(500),
    http_method character varying(16),
    menu_id character varying(64),
    permission_code character varying(200) NOT NULL,
    permission_name character varying(200) NOT NULL,
    permission_type character varying(32),
    resource_pattern character varying(500),
    resource_type character varying(64),
    scope_type character varying(32),
    status integer
);


--
-- Name: TABLE t_permission; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_permission IS '权限表';


--
-- Name: t_portal_application; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_portal_application (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    app_code character varying(80) NOT NULL,
    app_name character varying(120) NOT NULL,
    audience_department_ids character varying(4000),
    audience_organization_ids character varying(2000),
    audience_role_codes character varying(1000),
    description character varying(500),
    enabled boolean NOT NULL,
    icon character varying(100),
    open_mode character varying(20) NOT NULL,
    recommended boolean NOT NULL,
    required_permission character varying(200),
    route_path character varying(300) NOT NULL,
    sort_order integer
);


--
-- Name: TABLE t_portal_application; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_portal_application IS '门户应用';


--
-- Name: t_portal_widget; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_portal_widget (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    audience_department_ids character varying(4000),
    audience_organization_ids character varying(2000),
    audience_role_codes character varying(1000),
    component_name character varying(120) NOT NULL,
    default_size character varying(20) NOT NULL,
    description character varying(500),
    enabled boolean NOT NULL,
    provider_code character varying(80) NOT NULL,
    required_permission character varying(200),
    sort_order integer,
    widget_code character varying(80) NOT NULL,
    widget_name character varying(120) NOT NULL
);


--
-- Name: TABLE t_portal_widget; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_portal_widget IS '门户组件';


--
-- Name: t_position; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_position (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    description character varying(500),
    management boolean NOT NULL,
    position_category character varying(50),
    position_code character varying(100) NOT NULL,
    position_level character varying(50),
    position_name character varying(100) NOT NULL,
    sort_order integer NOT NULL,
    status integer NOT NULL
);


--
-- Name: TABLE t_position; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_position IS '岗位';


--
-- Name: t_refresh_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_refresh_token (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    expiry_time timestamp(6) without time zone NOT NULL,
    revoked boolean NOT NULL,
    token text NOT NULL,
    username character varying(200) NOT NULL
);


--
-- Name: TABLE t_refresh_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_refresh_token IS 'Refresh token store';


--
-- Name: t_resource; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_resource (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    entry_date character varying(20) NOT NULL,
    is_manager boolean NOT NULL,
    leave_date character varying(20),
    organization_unit_id character varying(64) NOT NULL,
    "position" character varying(100) NOT NULL,
    position_level character varying(100) NOT NULL,
    resource_code character varying(100) NOT NULL,
    resource_name character varying(100) NOT NULL,
    user_id character varying(64) NOT NULL
);


--
-- Name: TABLE t_resource; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_resource IS '员工表';


--
-- Name: t_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_role (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    built_in boolean,
    description character varying(500),
    role_code character varying(100),
    role_name character varying(100) NOT NULL,
    status integer
);


--
-- Name: TABLE t_role; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_role IS '角色表';


--
-- Name: t_role_data_scope; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_role_data_scope (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    employee_id character varying(64),
    organization_id character varying(64),
    organization_unit_id character varying(64),
    role_id character varying(64) NOT NULL,
    scope_type character varying(64) NOT NULL,
    resource_type character varying(64),
    resource_id character varying(64)
);


--
-- Name: TABLE t_role_data_scope; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_role_data_scope IS '角色数据权限';


--
-- Name: t_role_menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_role_menu (
    role_id character varying(64) NOT NULL,
    menu_id character varying(64) NOT NULL
);


--
-- Name: t_role_menu_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_role_menu_permission (
    menu_id character varying(64) NOT NULL,
    permission_id character varying(64) NOT NULL,
    role_id character varying(64) NOT NULL
);


--
-- Name: TABLE t_role_menu_permission; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_role_menu_permission IS '角色-菜单-权限关联';


--
-- Name: t_role_permission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_role_permission (
    permission_id character varying(64) NOT NULL,
    role_id character varying(64) NOT NULL
);


--
-- Name: TABLE t_role_permission; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.t_role_permission IS '角色权限';


--
-- Name: t_user_portal_application; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_user_portal_application (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    application_id character varying(64) NOT NULL,
    favorite boolean NOT NULL,
    favorite_order integer,
    last_visited_at timestamp(6) without time zone,
    username character varying(100) NOT NULL,
    visit_count bigint NOT NULL
);


--
-- Name: t_user_portal_preference; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_user_portal_preference (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    layout_json text NOT NULL,
    theme character varying(30) NOT NULL,
    username character varying(100) NOT NULL
);


--
-- Name: t_user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.t_user_role (
    user_id character varying(64) NOT NULL,
    role_id character varying(64) NOT NULL
);


--
-- Name: wf_ai_setting; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_ai_setting (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    allow_external boolean NOT NULL,
    enabled boolean NOT NULL,
    mask_sensitive_data boolean NOT NULL,
    provider_mode character varying(30) NOT NULL
);


--
-- Name: wf_definition; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_definition (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    ai_assist_enabled boolean NOT NULL,
    category character varying(100),
    config_json oid,
    description character varying(1000),
    entry_node_key character varying(100),
    flow_code character varying(100),
    flow_name character varying(160) NOT NULL,
    flowable_deployment_id character varying(64),
    flowable_process_key character varying(160),
    main_form_id character varying(64),
    manager_user character varying(128),
    published_at timestamp(6) without time zone,
    starter_scope_json oid,
    status character varying(30) NOT NULL,
    tags character varying(500),
    version character varying(40) NOT NULL
);


--
-- Name: wf_definition_acl; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_definition_acl (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    action character varying(32) NOT NULL,
    definition_id character varying(64) NOT NULL,
    enabled boolean NOT NULL,
    subject_id character varying(128) NOT NULL,
    subject_type character varying(32) NOT NULL
);


--
-- Name: wf_delegation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_delegation (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    definition_id character varying(64),
    delegatee character varying(128) NOT NULL,
    delegator character varying(128) NOT NULL,
    enabled boolean NOT NULL,
    end_at timestamp(6) without time zone NOT NULL,
    reason character varying(500),
    start_at timestamp(6) without time zone NOT NULL
);


--
-- Name: wf_edge; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_edge (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    condition_expr character varying(1000),
    flow_id character varying(64) NOT NULL,
    from_node_key character varying(100) NOT NULL,
    is_default boolean NOT NULL,
    to_node_key character varying(100) NOT NULL
);


--
-- Name: wf_execution_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_execution_log (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    duration_ms bigint,
    engine_instance_id character varying(64),
    error_message text,
    executor character varying(80) NOT NULL,
    finished_at timestamp(6) without time zone,
    instance_id character varying(64) NOT NULL,
    node_id character varying(64) NOT NULL,
    node_key character varying(100) NOT NULL,
    request_json text,
    response_json text,
    started_at timestamp(6) without time zone NOT NULL,
    status character varying(20) NOT NULL
);


--
-- Name: wf_idempotency; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_idempotency (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    actor character varying(128) NOT NULL,
    expires_at timestamp(6) without time zone NOT NULL,
    idempotency_key character varying(160) NOT NULL,
    operation character varying(64) NOT NULL,
    resource_id character varying(64),
    status character varying(30) NOT NULL
);


--
-- Name: wf_incident; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_incident (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    context_json text,
    engine_instance_id character varying(64),
    engine_job_id character varying(64),
    error_message text,
    execution_id character varying(64),
    incident_type character varying(40) NOT NULL,
    instance_id character varying(64),
    lock_version bigint DEFAULT 0 NOT NULL,
    next_retry_at timestamp(6) without time zone,
    node_key character varying(100),
    resolution character varying(1000),
    resolved_at timestamp(6) without time zone,
    resolved_by character varying(128),
    retry_count integer NOT NULL,
    status character varying(30) NOT NULL
);


--
-- Name: wf_instance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_instance (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    business_key character varying(160),
    current_node_key character varying(100),
    definition_id character varying(64) NOT NULL,
    definition_version character varying(40) NOT NULL,
    engine_instance_id character varying(64),
    engine_type character varying(20) DEFAULT 'LEGACY'::character varying NOT NULL,
    finished_at timestamp(6) without time zone,
    initiator character varying(128) NOT NULL,
    lock_version bigint DEFAULT 0 NOT NULL,
    status character varying(30) NOT NULL,
    variables_json oid
);


--
-- Name: wf_instance_participant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_instance_participant (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    active boolean NOT NULL,
    instance_id character varying(64) NOT NULL,
    participant_type character varying(32) NOT NULL,
    source_task_id character varying(64),
    username character varying(128) NOT NULL
);


--
-- Name: wf_node; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_node (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    additional_form_ids oid,
    executor character varying(160),
    field_permissions_json oid,
    flow_id character varying(64) NOT NULL,
    input_schema oid,
    node_key character varying(100) NOT NULL,
    node_name character varying(160) NOT NULL,
    node_type character varying(40) NOT NULL,
    output_schema oid,
    properties_json oid,
    retry_interval_sec integer,
    retry_max integer,
    timeout_sec integer
);


--
-- Name: wf_notification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_notification (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    content text NOT NULL,
    instance_id character varying(64),
    notification_type character varying(50) NOT NULL,
    read_at timestamp(6) without time zone,
    recipient character varying(128) NOT NULL,
    source_event_id character varying(64) NOT NULL,
    task_id character varying(64),
    title character varying(200) NOT NULL
);


--
-- Name: wf_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_outbox (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    aggregate_id character varying(64),
    attempts integer NOT NULL,
    deduplication_key character varying(200),
    event_type character varying(80) NOT NULL,
    last_error character varying(1000),
    next_attempt_at timestamp(6) without time zone,
    payload_json text NOT NULL,
    sent_at timestamp(6) without time zone,
    status character varying(30) NOT NULL
);


--
-- Name: wf_review; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_review (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    blocking boolean NOT NULL,
    category character varying(80) NOT NULL,
    config_hash character varying(64),
    description character varying(2000),
    flow_id character varying(64) NOT NULL,
    node_id character varying(64),
    severity character varying(20) NOT NULL,
    source character varying(20) NOT NULL,
    suggestion character varying(2000),
    title character varying(300) NOT NULL
);


--
-- Name: wf_task; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_task (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    assignee character varying(128),
    comment character varying(1000),
    completed_at timestamp(6) without time zone,
    due_at timestamp(6) without time zone,
    engine_task_id character varying(64),
    escalation_level integer DEFAULT 0 NOT NULL,
    instance_id character varying(64) NOT NULL,
    lock_version bigint DEFAULT 0 NOT NULL,
    next_reminder_at timestamp(6) without time zone,
    node_key character varying(100) NOT NULL,
    node_name character varying(160) NOT NULL,
    reminded_at timestamp(6) without time zone,
    reminder_count integer DEFAULT 0 NOT NULL,
    resume_node_key character varying(100),
    round_key character varying(64),
    sla_status character varying(30) DEFAULT 'NORMAL'::character varying NOT NULL,
    status character varying(30) NOT NULL,
    task_kind character varying(32) DEFAULT 'NORMAL'::character varying NOT NULL
);


--
-- Name: wf_task_candidate; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.wf_task_candidate (
    id character varying(64) NOT NULL,
    create_by character varying(128) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    last_update_by character varying(128),
    last_update_time timestamp(6) without time zone,
    subject_id character varying(128) NOT NULL,
    subject_type character varying(32) NOT NULL,
    task_id character varying(64) NOT NULL
);


--
-- Name: act_evt_log log_nr_; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_evt_log ALTER COLUMN log_nr_ SET DEFAULT nextval('public.act_evt_log_log_nr__seq'::regclass);


--
-- Name: act_hi_tsk_log id_; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_hi_tsk_log ALTER COLUMN id_ SET DEFAULT nextval('public.act_hi_tsk_log_id__seq'::regclass);


--
-- Name: sys_industry_data_sync_log id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_industry_data_sync_log ALTER COLUMN id SET DEFAULT nextval('public.sys_industry_data_sync_log_id_seq'::regclass);


--
-- Name: flw_channel_definition FLW_CHANNEL_DEFINITION_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flw_channel_definition
    ADD CONSTRAINT "FLW_CHANNEL_DEFINITION_pkey" PRIMARY KEY (id_);


--
-- Name: flw_event_definition FLW_EVENT_DEFINITION_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flw_event_definition
    ADD CONSTRAINT "FLW_EVENT_DEFINITION_pkey" PRIMARY KEY (id_);


--
-- Name: flw_event_deployment FLW_EVENT_DEPLOYMENT_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flw_event_deployment
    ADD CONSTRAINT "FLW_EVENT_DEPLOYMENT_pkey" PRIMARY KEY (id_);


--
-- Name: flw_event_resource FLW_EVENT_RESOURCE_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flw_event_resource
    ADD CONSTRAINT "FLW_EVENT_RESOURCE_pkey" PRIMARY KEY (id_);


--
-- Name: act_evt_log act_evt_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_evt_log
    ADD CONSTRAINT act_evt_log_pkey PRIMARY KEY (log_nr_);


--
-- Name: act_ge_bytearray act_ge_bytearray_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ge_bytearray
    ADD CONSTRAINT act_ge_bytearray_pkey PRIMARY KEY (id_);


--
-- Name: act_ge_property act_ge_property_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ge_property
    ADD CONSTRAINT act_ge_property_pkey PRIMARY KEY (name_);


--
-- Name: act_hi_actinst act_hi_actinst_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_hi_actinst
    ADD CONSTRAINT act_hi_actinst_pkey PRIMARY KEY (id_);


--
-- Name: act_hi_attachment act_hi_attachment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_hi_attachment
    ADD CONSTRAINT act_hi_attachment_pkey PRIMARY KEY (id_);


--
-- Name: act_hi_comment act_hi_comment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_hi_comment
    ADD CONSTRAINT act_hi_comment_pkey PRIMARY KEY (id_);


--
-- Name: act_hi_detail act_hi_detail_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_hi_detail
    ADD CONSTRAINT act_hi_detail_pkey PRIMARY KEY (id_);


--
-- Name: act_hi_entitylink act_hi_entitylink_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_hi_entitylink
    ADD CONSTRAINT act_hi_entitylink_pkey PRIMARY KEY (id_);


--
-- Name: act_hi_identitylink act_hi_identitylink_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_hi_identitylink
    ADD CONSTRAINT act_hi_identitylink_pkey PRIMARY KEY (id_);


--
-- Name: act_hi_procinst act_hi_procinst_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_hi_procinst
    ADD CONSTRAINT act_hi_procinst_pkey PRIMARY KEY (id_);


--
-- Name: act_hi_procinst act_hi_procinst_proc_inst_id__key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_hi_procinst
    ADD CONSTRAINT act_hi_procinst_proc_inst_id__key UNIQUE (proc_inst_id_);


--
-- Name: act_hi_taskinst act_hi_taskinst_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_hi_taskinst
    ADD CONSTRAINT act_hi_taskinst_pkey PRIMARY KEY (id_);


--
-- Name: act_hi_tsk_log act_hi_tsk_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_hi_tsk_log
    ADD CONSTRAINT act_hi_tsk_log_pkey PRIMARY KEY (id_);


--
-- Name: act_hi_varinst act_hi_varinst_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_hi_varinst
    ADD CONSTRAINT act_hi_varinst_pkey PRIMARY KEY (id_);


--
-- Name: act_id_bytearray act_id_bytearray_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_id_bytearray
    ADD CONSTRAINT act_id_bytearray_pkey PRIMARY KEY (id_);


--
-- Name: act_id_group act_id_group_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_id_group
    ADD CONSTRAINT act_id_group_pkey PRIMARY KEY (id_);


--
-- Name: act_id_info act_id_info_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_id_info
    ADD CONSTRAINT act_id_info_pkey PRIMARY KEY (id_);


--
-- Name: act_id_membership act_id_membership_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_id_membership
    ADD CONSTRAINT act_id_membership_pkey PRIMARY KEY (user_id_, group_id_);


--
-- Name: act_id_priv_mapping act_id_priv_mapping_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_id_priv_mapping
    ADD CONSTRAINT act_id_priv_mapping_pkey PRIMARY KEY (id_);


--
-- Name: act_id_priv act_id_priv_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_id_priv
    ADD CONSTRAINT act_id_priv_pkey PRIMARY KEY (id_);


--
-- Name: act_id_property act_id_property_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_id_property
    ADD CONSTRAINT act_id_property_pkey PRIMARY KEY (name_);


--
-- Name: act_id_token act_id_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_id_token
    ADD CONSTRAINT act_id_token_pkey PRIMARY KEY (id_);


--
-- Name: act_id_user act_id_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_id_user
    ADD CONSTRAINT act_id_user_pkey PRIMARY KEY (id_);


--
-- Name: act_procdef_info act_procdef_info_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_procdef_info
    ADD CONSTRAINT act_procdef_info_pkey PRIMARY KEY (id_);


--
-- Name: act_re_deployment act_re_deployment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_re_deployment
    ADD CONSTRAINT act_re_deployment_pkey PRIMARY KEY (id_);


--
-- Name: act_re_model act_re_model_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_re_model
    ADD CONSTRAINT act_re_model_pkey PRIMARY KEY (id_);


--
-- Name: act_re_procdef act_re_procdef_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_re_procdef
    ADD CONSTRAINT act_re_procdef_pkey PRIMARY KEY (id_);


--
-- Name: act_ru_actinst act_ru_actinst_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_actinst
    ADD CONSTRAINT act_ru_actinst_pkey PRIMARY KEY (id_);


--
-- Name: act_ru_deadletter_job act_ru_deadletter_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_deadletter_job
    ADD CONSTRAINT act_ru_deadletter_job_pkey PRIMARY KEY (id_);


--
-- Name: act_ru_entitylink act_ru_entitylink_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_entitylink
    ADD CONSTRAINT act_ru_entitylink_pkey PRIMARY KEY (id_);


--
-- Name: act_ru_event_subscr act_ru_event_subscr_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_event_subscr
    ADD CONSTRAINT act_ru_event_subscr_pkey PRIMARY KEY (id_);


--
-- Name: act_ru_execution act_ru_execution_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_execution
    ADD CONSTRAINT act_ru_execution_pkey PRIMARY KEY (id_);


--
-- Name: act_ru_external_job act_ru_external_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_external_job
    ADD CONSTRAINT act_ru_external_job_pkey PRIMARY KEY (id_);


--
-- Name: act_ru_history_job act_ru_history_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_history_job
    ADD CONSTRAINT act_ru_history_job_pkey PRIMARY KEY (id_);


--
-- Name: act_ru_identitylink act_ru_identitylink_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_identitylink
    ADD CONSTRAINT act_ru_identitylink_pkey PRIMARY KEY (id_);


--
-- Name: act_ru_job act_ru_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_job
    ADD CONSTRAINT act_ru_job_pkey PRIMARY KEY (id_);


--
-- Name: act_ru_suspended_job act_ru_suspended_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_suspended_job
    ADD CONSTRAINT act_ru_suspended_job_pkey PRIMARY KEY (id_);


--
-- Name: act_ru_task act_ru_task_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_task
    ADD CONSTRAINT act_ru_task_pkey PRIMARY KEY (id_);


--
-- Name: act_ru_timer_job act_ru_timer_job_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_timer_job
    ADD CONSTRAINT act_ru_timer_job_pkey PRIMARY KEY (id_);


--
-- Name: act_ru_variable act_ru_variable_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_variable
    ADD CONSTRAINT act_ru_variable_pkey PRIMARY KEY (id_);


--
-- Name: act_procdef_info act_uniq_info_procdef; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_procdef_info
    ADD CONSTRAINT act_uniq_info_procdef UNIQUE (proc_def_id_);


--
-- Name: act_id_priv act_uniq_priv_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_id_priv
    ADD CONSTRAINT act_uniq_priv_name UNIQUE (name_);


--
-- Name: act_re_procdef act_uniq_procdef; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_re_procdef
    ADD CONSTRAINT act_uniq_procdef UNIQUE (key_, version_, derived_version_, tenant_id_);


--
-- Name: ai_model_config ai_model_config_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_model_config
    ADD CONSTRAINT ai_model_config_pkey PRIMARY KEY (id);


--
-- Name: d_capability d_capability_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_capability
    ADD CONSTRAINT d_capability_pkey PRIMARY KEY (id);


--
-- Name: d_resource_capability d_resource_capability_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_resource_capability
    ADD CONSTRAINT d_resource_capability_pkey PRIMARY KEY (id);


--
-- Name: d_resource d_resource_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_resource
    ADD CONSTRAINT d_resource_pkey PRIMARY KEY (id);


--
-- Name: d_schedule_assignment d_schedule_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_schedule_assignment
    ADD CONSTRAINT d_schedule_assignment_pkey PRIMARY KEY (id);


--
-- Name: d_schedule_result d_schedule_result_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_schedule_result
    ADD CONSTRAINT d_schedule_result_pkey PRIMARY KEY (id);


--
-- Name: d_schedule_rule d_schedule_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_schedule_rule
    ADD CONSTRAINT d_schedule_rule_pkey PRIMARY KEY (id);


--
-- Name: d_task_capability_requirement d_task_capability_requirement_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_task_capability_requirement
    ADD CONSTRAINT d_task_capability_requirement_pkey PRIMARY KEY (id);


--
-- Name: d_task d_task_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_task
    ADD CONSTRAINT d_task_pkey PRIMARY KEY (id);


--
-- Name: edu_academic_term edu_academic_term_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_academic_term
    ADD CONSTRAINT edu_academic_term_pkey PRIMARY KEY (id);


--
-- Name: edu_administrative_class edu_administrative_class_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_administrative_class
    ADD CONSTRAINT edu_administrative_class_pkey PRIMARY KEY (id);


--
-- Name: edu_classroom edu_classroom_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_classroom
    ADD CONSTRAINT edu_classroom_pkey PRIMARY KEY (id);


--
-- Name: edu_course_adjustment_record edu_course_adjustment_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_course_adjustment_record
    ADD CONSTRAINT edu_course_adjustment_record_pkey PRIMARY KEY (id);


--
-- Name: edu_course_catalog edu_course_catalog_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_course_catalog
    ADD CONSTRAINT edu_course_catalog_pkey PRIMARY KEY (id);


--
-- Name: edu_course_offering edu_course_offering_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_course_offering
    ADD CONSTRAINT edu_course_offering_pkey PRIMARY KEY (id);


--
-- Name: edu_grade edu_grade_grade_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_grade
    ADD CONSTRAINT edu_grade_grade_code_key UNIQUE (grade_code);


--
-- Name: edu_grade edu_grade_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_grade
    ADD CONSTRAINT edu_grade_pkey PRIMARY KEY (id);


--
-- Name: edu_leave_request edu_leave_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_leave_request
    ADD CONSTRAINT edu_leave_request_pkey PRIMARY KEY (id);


--
-- Name: edu_major edu_major_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_major
    ADD CONSTRAINT edu_major_pkey PRIMARY KEY (id);


--
-- Name: edu_parent_profile edu_parent_profile_parent_no_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_parent_profile
    ADD CONSTRAINT edu_parent_profile_parent_no_key UNIQUE (parent_no);


--
-- Name: edu_parent_profile edu_parent_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_parent_profile
    ADD CONSTRAINT edu_parent_profile_pkey PRIMARY KEY (id);


--
-- Name: edu_schedule_candidate_plan edu_schedule_candidate_plan_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_schedule_candidate_plan
    ADD CONSTRAINT edu_schedule_candidate_plan_pkey PRIMARY KEY (id);


--
-- Name: edu_schedule_entry edu_schedule_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_schedule_entry
    ADD CONSTRAINT edu_schedule_entry_pkey PRIMARY KEY (id);


--
-- Name: edu_schedule_plan_version edu_schedule_plan_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_schedule_plan_version
    ADD CONSTRAINT edu_schedule_plan_version_pkey PRIMARY KEY (id);


--
-- Name: edu_scheduling_agent_proposal edu_scheduling_agent_proposal_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_scheduling_agent_proposal
    ADD CONSTRAINT edu_scheduling_agent_proposal_pkey PRIMARY KEY (id);


--
-- Name: edu_student_guardian edu_student_guardian_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_student_guardian
    ADD CONSTRAINT edu_student_guardian_pkey PRIMARY KEY (id);


--
-- Name: edu_student_profile edu_student_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_student_profile
    ADD CONSTRAINT edu_student_profile_pkey PRIMARY KEY (id);


--
-- Name: edu_subject edu_subject_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_subject
    ADD CONSTRAINT edu_subject_pkey PRIMARY KEY (id);


--
-- Name: edu_subject edu_subject_subject_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_subject
    ADD CONSTRAINT edu_subject_subject_code_key UNIQUE (subject_code);


--
-- Name: edu_teacher_profile edu_teacher_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_teacher_profile
    ADD CONSTRAINT edu_teacher_profile_pkey PRIMARY KEY (id);


--
-- Name: edu_teacher_teaching_assignment edu_teacher_teaching_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_teacher_teaching_assignment
    ADD CONSTRAINT edu_teacher_teaching_assignment_pkey PRIMARY KEY (id);


--
-- Name: edu_teacher_time_constraint edu_teacher_time_constraint_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_teacher_time_constraint
    ADD CONSTRAINT edu_teacher_time_constraint_pkey PRIMARY KEY (id);


--
-- Name: edu_teaching_class_member edu_teaching_class_member_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_teaching_class_member
    ADD CONSTRAINT edu_teaching_class_member_pkey PRIMARY KEY (id);


--
-- Name: edu_user_profile_binding edu_user_profile_binding_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_user_profile_binding
    ADD CONSTRAINT edu_user_profile_binding_pkey PRIMARY KEY (id);


--
-- Name: flw_ru_batch_part flw_ru_batch_part_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flw_ru_batch_part
    ADD CONSTRAINT flw_ru_batch_part_pkey PRIMARY KEY (id_);


--
-- Name: flw_ru_batch flw_ru_batch_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flw_ru_batch
    ADD CONSTRAINT flw_ru_batch_pkey PRIMARY KEY (id_);


--
-- Name: form_definition form_definition_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.form_definition
    ADD CONSTRAINT form_definition_pkey PRIMARY KEY (id);


--
-- Name: form_field form_field_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.form_field
    ADD CONSTRAINT form_field_pkey PRIMARY KEY (id);


--
-- Name: form_instance form_instance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.form_instance
    ADD CONSTRAINT form_instance_pkey PRIMARY KEY (id);


--
-- Name: kb_document_chunk kb_document_chunk_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_chunk
    ADD CONSTRAINT kb_document_chunk_pkey PRIMARY KEY (id);


--
-- Name: kb_document kb_document_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document
    ADD CONSTRAINT kb_document_pkey PRIMARY KEY (id);


--
-- Name: kb_knowledge_base kb_knowledge_base_base_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_base
    ADD CONSTRAINT kb_knowledge_base_base_code_key UNIQUE (base_code);


--
-- Name: kb_knowledge_base kb_knowledge_base_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_knowledge_base
    ADD CONSTRAINT kb_knowledge_base_pkey PRIMARY KEY (id);


--
-- Name: msg_channel_policy msg_channel_policy_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_channel_policy
    ADD CONSTRAINT msg_channel_policy_pkey PRIMARY KEY (id);


--
-- Name: msg_channel_preference msg_channel_preference_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_channel_preference
    ADD CONSTRAINT msg_channel_preference_pkey PRIMARY KEY (id);


--
-- Name: msg_notification_template msg_notification_template_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_notification_template
    ADD CONSTRAINT msg_notification_template_pkey PRIMARY KEY (id);


--
-- Name: msg_publication_attachment msg_publication_attachment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_publication_attachment
    ADD CONSTRAINT msg_publication_attachment_pkey PRIMARY KEY (id);


--
-- Name: msg_publication_audience msg_publication_audience_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_publication_audience
    ADD CONSTRAINT msg_publication_audience_pkey PRIMARY KEY (id);


--
-- Name: msg_publication_delivery msg_publication_delivery_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_publication_delivery
    ADD CONSTRAINT msg_publication_delivery_pkey PRIMARY KEY (id);


--
-- Name: msg_publication msg_publication_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_publication
    ADD CONSTRAINT msg_publication_pkey PRIMARY KEY (id);


--
-- Name: msg_publication_read msg_publication_read_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_publication_read
    ADD CONSTRAINT msg_publication_read_pkey PRIMARY KEY (id);


--
-- Name: msg_publication_recipient msg_publication_recipient_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_publication_recipient
    ADD CONSTRAINT msg_publication_recipient_pkey PRIMARY KEY (id);


--
-- Name: msg_publication_version msg_publication_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_publication_version
    ADD CONSTRAINT msg_publication_version_pkey PRIMARY KEY (id);


--
-- Name: sys_industry_data_sync_log sys_industry_data_sync_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_industry_data_sync_log
    ADD CONSTRAINT sys_industry_data_sync_log_pkey PRIMARY KEY (id);


--
-- Name: sys_industry_template_installation sys_industry_template_installation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_industry_template_installation
    ADD CONSTRAINT sys_industry_template_installation_pkey PRIMARY KEY (id);


--
-- Name: t_admin_user t_admin_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_admin_user
    ADD CONSTRAINT t_admin_user_pkey PRIMARY KEY (id);


--
-- Name: t_audit_log t_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_audit_log
    ADD CONSTRAINT t_audit_log_pkey PRIMARY KEY (id);


--
-- Name: t_consumer_user t_consumer_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_consumer_user
    ADD CONSTRAINT t_consumer_user_pkey PRIMARY KEY (id);


--
-- Name: t_dict t_dict_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_dict
    ADD CONSTRAINT t_dict_pkey PRIMARY KEY (id);


--
-- Name: t_employee_assignment t_employee_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_employee_assignment
    ADD CONSTRAINT t_employee_assignment_pkey PRIMARY KEY (id);


--
-- Name: t_iam_employee t_iam_employee_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_iam_employee
    ADD CONSTRAINT t_iam_employee_pkey PRIMARY KEY (id);


--
-- Name: t_job_level t_job_level_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_job_level
    ADD CONSTRAINT t_job_level_pkey PRIMARY KEY (id);


--
-- Name: t_job_title t_job_title_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_job_title
    ADD CONSTRAINT t_job_title_pkey PRIMARY KEY (id);


--
-- Name: t_managed_file t_managed_file_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_managed_file
    ADD CONSTRAINT t_managed_file_pkey PRIMARY KEY (id);


--
-- Name: t_menu t_menu_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_menu
    ADD CONSTRAINT t_menu_pkey PRIMARY KEY (id);


--
-- Name: t_organization t_organization_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_organization
    ADD CONSTRAINT t_organization_pkey PRIMARY KEY (id);


--
-- Name: t_organization_unit t_organization_unit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_organization_unit
    ADD CONSTRAINT t_organization_unit_pkey PRIMARY KEY (id);


--
-- Name: t_permission t_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_permission
    ADD CONSTRAINT t_permission_pkey PRIMARY KEY (id);


--
-- Name: t_portal_application t_portal_application_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_portal_application
    ADD CONSTRAINT t_portal_application_pkey PRIMARY KEY (id);


--
-- Name: t_portal_widget t_portal_widget_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_portal_widget
    ADD CONSTRAINT t_portal_widget_pkey PRIMARY KEY (id);


--
-- Name: t_position t_position_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_position
    ADD CONSTRAINT t_position_pkey PRIMARY KEY (id);


--
-- Name: t_refresh_token t_refresh_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_refresh_token
    ADD CONSTRAINT t_refresh_token_pkey PRIMARY KEY (id);


--
-- Name: t_resource t_resource_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_resource
    ADD CONSTRAINT t_resource_pkey PRIMARY KEY (id);


--
-- Name: t_role_data_scope t_role_data_scope_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_role_data_scope
    ADD CONSTRAINT t_role_data_scope_pkey PRIMARY KEY (id);


--
-- Name: t_role_menu_permission t_role_menu_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_role_menu_permission
    ADD CONSTRAINT t_role_menu_permission_pkey PRIMARY KEY (menu_id, permission_id, role_id);


--
-- Name: t_role_menu t_role_menu_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_role_menu
    ADD CONSTRAINT t_role_menu_pkey PRIMARY KEY (role_id, menu_id);


--
-- Name: t_role_permission t_role_permission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_role_permission
    ADD CONSTRAINT t_role_permission_pkey PRIMARY KEY (permission_id, role_id);


--
-- Name: t_role t_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_role
    ADD CONSTRAINT t_role_pkey PRIMARY KEY (id);


--
-- Name: t_user_portal_application t_user_portal_application_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user_portal_application
    ADD CONSTRAINT t_user_portal_application_pkey PRIMARY KEY (id);


--
-- Name: t_user_portal_preference t_user_portal_preference_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user_portal_preference
    ADD CONSTRAINT t_user_portal_preference_pkey PRIMARY KEY (id);


--
-- Name: t_user_role t_user_role_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user_role
    ADD CONSTRAINT t_user_role_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: t_portal_application uk1q1edwin6bhadoipqy6pa24gq; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_portal_application
    ADD CONSTRAINT uk1q1edwin6bhadoipqy6pa24gq UNIQUE (app_code);


--
-- Name: wf_definition uk6hbnp4dt04qcmpaqntvsrvlb7; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_definition
    ADD CONSTRAINT uk6hbnp4dt04qcmpaqntvsrvlb7 UNIQUE (flow_code, version);


--
-- Name: t_permission uk7m35n7a1uot3w8twmu1pjuc3l; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_permission
    ADD CONSTRAINT uk7m35n7a1uot3w8twmu1pjuc3l UNIQUE (permission_code);


--
-- Name: wf_task uk8wi8p2vlcrycqfp3vfc2pxb2e; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_task
    ADD CONSTRAINT uk8wi8p2vlcrycqfp3vfc2pxb2e UNIQUE (engine_task_id);


--
-- Name: t_admin_user uk8xbufll7jsly3ode3afe3h4l; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_admin_user
    ADD CONSTRAINT uk8xbufll7jsly3ode3afe3h4l UNIQUE (username);


--
-- Name: t_iam_employee uk9g5ggkiy5o4v7ybfil2ocb5f; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_iam_employee
    ADD CONSTRAINT uk9g5ggkiy5o4v7ybfil2ocb5f UNIQUE (employee_code);


--
-- Name: edu_course_adjustment_record uk_edu_adjustment_workflow_instance; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_course_adjustment_record
    ADD CONSTRAINT uk_edu_adjustment_workflow_instance UNIQUE (workflow_instance_id);


--
-- Name: edu_classroom uk_edu_classroom_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_classroom
    ADD CONSTRAINT uk_edu_classroom_code UNIQUE (room_code);


--
-- Name: edu_course_offering uk_edu_course_offering_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_course_offering
    ADD CONSTRAINT uk_edu_course_offering_code UNIQUE (semester_code, offering_code);


--
-- Name: edu_leave_request uk_edu_leave_workflow_instance; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_leave_request
    ADD CONSTRAINT uk_edu_leave_workflow_instance UNIQUE (workflow_instance_id);


--
-- Name: edu_schedule_entry uk_edu_schedule_offering_slot; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_schedule_entry
    ADD CONSTRAINT uk_edu_schedule_offering_slot UNIQUE (offering_id, day_of_week, period_no, start_week, end_week);


--
-- Name: edu_schedule_plan_version uk_edu_schedule_plan_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_schedule_plan_version
    ADD CONSTRAINT uk_edu_schedule_plan_version UNIQUE (semester_code, version_no);


--
-- Name: edu_student_guardian uk_edu_student_guardian; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_student_guardian
    ADD CONSTRAINT uk_edu_student_guardian UNIQUE (student_id, parent_id);


--
-- Name: edu_teaching_class_member uk_edu_teaching_class_member; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_teaching_class_member
    ADD CONSTRAINT uk_edu_teaching_class_member UNIQUE (offering_id, student_id);


--
-- Name: edu_user_profile_binding uk_edu_user_profile_binding; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_user_profile_binding
    ADD CONSTRAINT uk_edu_user_profile_binding UNIQUE (username, profile_type);


--
-- Name: sys_industry_template_installation uk_industry_template_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sys_industry_template_installation
    ADD CONSTRAINT uk_industry_template_code UNIQUE (industry_code);


--
-- Name: kb_document_chunk uk_kb_chunk_document_index; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_chunk
    ADD CONSTRAINT uk_kb_chunk_document_index UNIQUE (document_id, chunk_index);


--
-- Name: t_managed_file uk_managed_file_storage_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_managed_file
    ADD CONSTRAINT uk_managed_file_storage_key UNIQUE (storage_key);


--
-- Name: msg_channel_preference uk_msg_channel_preference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_channel_preference
    ADD CONSTRAINT uk_msg_channel_preference UNIQUE (username, channel);


--
-- Name: msg_notification_template uk_msg_notification_template; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_notification_template
    ADD CONSTRAINT uk_msg_notification_template UNIQUE (template_code, channel);


--
-- Name: msg_publication_audience uk_msg_publication_audience; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_publication_audience
    ADD CONSTRAINT uk_msg_publication_audience UNIQUE (publication_id, subject_type, subject_id, excluded);


--
-- Name: msg_publication_delivery uk_msg_publication_delivery; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_publication_delivery
    ADD CONSTRAINT uk_msg_publication_delivery UNIQUE (publication_id, username, channel);


--
-- Name: msg_publication_read uk_msg_publication_read; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_publication_read
    ADD CONSTRAINT uk_msg_publication_read UNIQUE (publication_id, username);


--
-- Name: msg_publication_recipient uk_msg_publication_recipient; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_publication_recipient
    ADD CONSTRAINT uk_msg_publication_recipient UNIQUE (publication_id, username);


--
-- Name: msg_publication_version uk_msg_publication_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_publication_version
    ADD CONSTRAINT uk_msg_publication_version UNIQUE (publication_id, version_no);


--
-- Name: t_user_portal_preference uk_portal_preference_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user_portal_preference
    ADD CONSTRAINT uk_portal_preference_user UNIQUE (username);


--
-- Name: t_user_portal_application uk_user_portal_app; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user_portal_application
    ADD CONSTRAINT uk_user_portal_app UNIQUE (username, application_id);


--
-- Name: wf_definition_acl uk_wf_acl_rule; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_definition_acl
    ADD CONSTRAINT uk_wf_acl_rule UNIQUE (definition_id, subject_type, subject_id, action);


--
-- Name: wf_task_candidate uk_wf_candidate; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_task_candidate
    ADD CONSTRAINT uk_wf_candidate UNIQUE (task_id, subject_type, subject_id);


--
-- Name: wf_idempotency uk_wf_idempotency; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_idempotency
    ADD CONSTRAINT uk_wf_idempotency UNIQUE (actor, operation, idempotency_key);


--
-- Name: wf_incident uk_wf_incident_engine_job; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_incident
    ADD CONSTRAINT uk_wf_incident_engine_job UNIQUE (engine_job_id);


--
-- Name: wf_instance_participant uk_wf_participant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_instance_participant
    ADD CONSTRAINT uk_wf_participant UNIQUE (instance_id, username, participant_type);


--
-- Name: t_refresh_token ukafskc8y3trx2kv9ygkntnl5es; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_refresh_token
    ADD CONSTRAINT ukafskc8y3trx2kv9ygkntnl5es UNIQUE (token);


--
-- Name: edu_student_profile ukautinyxy117yfc7bgx6jxw6p0; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_student_profile
    ADD CONSTRAINT ukautinyxy117yfc7bgx6jxw6p0 UNIQUE (student_no);


--
-- Name: t_consumer_user ukbrr8ejdk74y7s1xa9v8q1g4cc; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_consumer_user
    ADD CONSTRAINT ukbrr8ejdk74y7s1xa9v8q1g4cc UNIQUE (phone);


--
-- Name: t_consumer_user ukcj1wmc5ala7ityh1ju1afacji; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_consumer_user
    ADD CONSTRAINT ukcj1wmc5ala7ityh1ju1afacji UNIQUE (username);


--
-- Name: msg_channel_policy ukd6ylih4b3kixsdva9tfijc4m7; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_channel_policy
    ADD CONSTRAINT ukd6ylih4b3kixsdva9tfijc4m7 UNIQUE (channel);


--
-- Name: form_instance ukdqhg7f3o0l0jgin4a81lsrrdo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.form_instance
    ADD CONSTRAINT ukdqhg7f3o0l0jgin4a81lsrrdo UNIQUE (workflow_instance_id, form_id, node_key);


--
-- Name: edu_academic_term ukdveot5ptf4g9par2c93b6athk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_academic_term
    ADD CONSTRAINT ukdveot5ptf4g9par2c93b6athk UNIQUE (term_code);


--
-- Name: msg_publication_attachment ukgehddq00lhx2jp2mtuadygg09; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.msg_publication_attachment
    ADD CONSTRAINT ukgehddq00lhx2jp2mtuadygg09 UNIQUE (storage_key);


--
-- Name: t_organization ukgf4v5gxlt9edno62o0s7a783r; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_organization
    ADD CONSTRAINT ukgf4v5gxlt9edno62o0s7a783r UNIQUE (org_code);


--
-- Name: edu_course_catalog ukghv8ov8ldj4boi1qqg8o6nkw4; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_course_catalog
    ADD CONSTRAINT ukghv8ov8ldj4boi1qqg8o6nkw4 UNIQUE (course_code);


--
-- Name: t_role ukgxuuxotqomykm13o7k8g03js1; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_role
    ADD CONSTRAINT ukgxuuxotqomykm13o7k8g03js1 UNIQUE (role_code);


--
-- Name: wf_instance ukh92fgptcodqtyo76oda5ledgo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_instance
    ADD CONSTRAINT ukh92fgptcodqtyo76oda5ledgo UNIQUE (engine_instance_id);


--
-- Name: form_field ukicx1m87cufvxlyyc2nofl6qdy; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.form_field
    ADD CONSTRAINT ukicx1m87cufvxlyyc2nofl6qdy UNIQUE (form_id, field_key);


--
-- Name: edu_teacher_profile ukiikl4yxe32rmpm3bjxj1t9d23; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_teacher_profile
    ADD CONSTRAINT ukiikl4yxe32rmpm3bjxj1t9d23 UNIQUE (teacher_no);


--
-- Name: t_job_title ukiukri1ua6lruraef9pqwyu0w7; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_job_title
    ADD CONSTRAINT ukiukri1ua6lruraef9pqwyu0w7 UNIQUE (title_code);


--
-- Name: t_position ukjvmie4w7ls34lm32n5p5xy3n; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_position
    ADD CONSTRAINT ukjvmie4w7ls34lm32n5p5xy3n UNIQUE (position_code);


--
-- Name: edu_major ukkgld6dqp53k5a8f9pr378jl1v; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_major
    ADD CONSTRAINT ukkgld6dqp53k5a8f9pr378jl1v UNIQUE (major_code);


--
-- Name: edu_teacher_profile ukko138xsd7k06u6ja6g9n1vjnr; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_teacher_profile
    ADD CONSTRAINT ukko138xsd7k06u6ja6g9n1vjnr UNIQUE (employee_id);


--
-- Name: t_portal_widget ukks7v131srrhxvk6ag6ucd302r; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_portal_widget
    ADD CONSTRAINT ukks7v131srrhxvk6ag6ucd302r UNIQUE (widget_code);


--
-- Name: t_job_level uklh419pv7gso1w9br59g45nrb3; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_job_level
    ADD CONSTRAINT uklh419pv7gso1w9br59g45nrb3 UNIQUE (level_code);


--
-- Name: wf_outbox ukm8skee79awcg3moiygi80ov2c; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_outbox
    ADD CONSTRAINT ukm8skee79awcg3moiygi80ov2c UNIQUE (deduplication_key);


--
-- Name: wf_notification ukmlkwhg3nbqniu75be0bd9mo7u; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_notification
    ADD CONSTRAINT ukmlkwhg3nbqniu75be0bd9mo7u UNIQUE (source_event_id);


--
-- Name: form_definition ukn3pwcgabsd95mj2hxp1kh25v6; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.form_definition
    ADD CONSTRAINT ukn3pwcgabsd95mj2hxp1kh25v6 UNIQUE (form_key, version);


--
-- Name: edu_administrative_class ukn8fburc48fd727n7npi6urvfy; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_administrative_class
    ADD CONSTRAINT ukn8fburc48fd727n7npi6urvfy UNIQUE (class_code);


--
-- Name: wf_node uktdhbxaw2hlwqyq0kxlh8jm8sy; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_node
    ADD CONSTRAINT uktdhbxaw2hlwqyq0kxlh8jm8sy UNIQUE (flow_id, node_key);


--
-- Name: wf_ai_setting wf_ai_setting_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_ai_setting
    ADD CONSTRAINT wf_ai_setting_pkey PRIMARY KEY (id);


--
-- Name: wf_definition_acl wf_definition_acl_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_definition_acl
    ADD CONSTRAINT wf_definition_acl_pkey PRIMARY KEY (id);


--
-- Name: wf_definition wf_definition_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_definition
    ADD CONSTRAINT wf_definition_pkey PRIMARY KEY (id);


--
-- Name: wf_delegation wf_delegation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_delegation
    ADD CONSTRAINT wf_delegation_pkey PRIMARY KEY (id);


--
-- Name: wf_edge wf_edge_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_edge
    ADD CONSTRAINT wf_edge_pkey PRIMARY KEY (id);


--
-- Name: wf_execution_log wf_execution_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_execution_log
    ADD CONSTRAINT wf_execution_log_pkey PRIMARY KEY (id);


--
-- Name: wf_idempotency wf_idempotency_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_idempotency
    ADD CONSTRAINT wf_idempotency_pkey PRIMARY KEY (id);


--
-- Name: wf_incident wf_incident_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_incident
    ADD CONSTRAINT wf_incident_pkey PRIMARY KEY (id);


--
-- Name: wf_instance_participant wf_instance_participant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_instance_participant
    ADD CONSTRAINT wf_instance_participant_pkey PRIMARY KEY (id);


--
-- Name: wf_instance wf_instance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_instance
    ADD CONSTRAINT wf_instance_pkey PRIMARY KEY (id);


--
-- Name: wf_node wf_node_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_node
    ADD CONSTRAINT wf_node_pkey PRIMARY KEY (id);


--
-- Name: wf_notification wf_notification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_notification
    ADD CONSTRAINT wf_notification_pkey PRIMARY KEY (id);


--
-- Name: wf_outbox wf_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_outbox
    ADD CONSTRAINT wf_outbox_pkey PRIMARY KEY (id);


--
-- Name: wf_review wf_review_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_review
    ADD CONSTRAINT wf_review_pkey PRIMARY KEY (id);


--
-- Name: wf_task_candidate wf_task_candidate_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_task_candidate
    ADD CONSTRAINT wf_task_candidate_pkey PRIMARY KEY (id);


--
-- Name: wf_task wf_task_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.wf_task
    ADD CONSTRAINT wf_task_pkey PRIMARY KEY (id);


--
-- Name: act_idx_act_hi_tsk_log_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_act_hi_tsk_log_task ON public.act_hi_tsk_log USING btree (task_id_);


--
-- Name: act_idx_athrz_procedef; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_athrz_procedef ON public.act_ru_identitylink USING btree (proc_def_id_);


--
-- Name: act_idx_bytear_depl; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_bytear_depl ON public.act_ge_bytearray USING btree (deployment_id_);


--
-- Name: act_idx_channel_def_uniq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX act_idx_channel_def_uniq ON public.flw_channel_definition USING btree (key_, version_, tenant_id_);


--
-- Name: act_idx_deadletter_job_correlation_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_deadletter_job_correlation_id ON public.act_ru_deadletter_job USING btree (correlation_id_);


--
-- Name: act_idx_deadletter_job_custom_values_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_deadletter_job_custom_values_id ON public.act_ru_deadletter_job USING btree (custom_values_id_);


--
-- Name: act_idx_deadletter_job_exception_stack_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_deadletter_job_exception_stack_id ON public.act_ru_deadletter_job USING btree (exception_stack_id_);


--
-- Name: act_idx_deadletter_job_execution_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_deadletter_job_execution_id ON public.act_ru_deadletter_job USING btree (execution_id_);


--
-- Name: act_idx_deadletter_job_proc_def_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_deadletter_job_proc_def_id ON public.act_ru_deadletter_job USING btree (proc_def_id_);


--
-- Name: act_idx_deadletter_job_process_instance_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_deadletter_job_process_instance_id ON public.act_ru_deadletter_job USING btree (process_instance_id_);


--
-- Name: act_idx_djob_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_djob_scope ON public.act_ru_deadletter_job USING btree (scope_id_, scope_type_);


--
-- Name: act_idx_djob_scope_def; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_djob_scope_def ON public.act_ru_deadletter_job USING btree (scope_definition_id_, scope_type_);


--
-- Name: act_idx_djob_sub_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_djob_sub_scope ON public.act_ru_deadletter_job USING btree (sub_scope_id_, scope_type_);


--
-- Name: act_idx_ejob_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ejob_scope ON public.act_ru_external_job USING btree (scope_id_, scope_type_);


--
-- Name: act_idx_ejob_scope_def; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ejob_scope_def ON public.act_ru_external_job USING btree (scope_definition_id_, scope_type_);


--
-- Name: act_idx_ejob_sub_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ejob_sub_scope ON public.act_ru_external_job USING btree (sub_scope_id_, scope_type_);


--
-- Name: act_idx_ent_lnk_ref_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ent_lnk_ref_scope ON public.act_ru_entitylink USING btree (ref_scope_id_, ref_scope_type_, link_type_);


--
-- Name: act_idx_ent_lnk_root_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ent_lnk_root_scope ON public.act_ru_entitylink USING btree (root_scope_id_, root_scope_type_, link_type_);


--
-- Name: act_idx_ent_lnk_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ent_lnk_scope ON public.act_ru_entitylink USING btree (scope_id_, scope_type_, link_type_);


--
-- Name: act_idx_ent_lnk_scope_def; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ent_lnk_scope_def ON public.act_ru_entitylink USING btree (scope_definition_id_, scope_type_, link_type_);


--
-- Name: act_idx_event_def_uniq; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX act_idx_event_def_uniq ON public.flw_event_definition USING btree (key_, version_, tenant_id_);


--
-- Name: act_idx_event_subscr; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_event_subscr ON public.act_ru_event_subscr USING btree (execution_id_);


--
-- Name: act_idx_event_subscr_config_; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_event_subscr_config_ ON public.act_ru_event_subscr USING btree (configuration_);


--
-- Name: act_idx_event_subscr_proc_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_event_subscr_proc_id ON public.act_ru_event_subscr USING btree (proc_inst_id_);


--
-- Name: act_idx_event_subscr_scoperef_; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_event_subscr_scoperef_ ON public.act_ru_event_subscr USING btree (scope_id_, scope_type_);


--
-- Name: act_idx_exe_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_exe_parent ON public.act_ru_execution USING btree (parent_id_);


--
-- Name: act_idx_exe_procdef; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_exe_procdef ON public.act_ru_execution USING btree (proc_def_id_);


--
-- Name: act_idx_exe_procinst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_exe_procinst ON public.act_ru_execution USING btree (proc_inst_id_);


--
-- Name: act_idx_exe_root; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_exe_root ON public.act_ru_execution USING btree (root_proc_inst_id_);


--
-- Name: act_idx_exe_super; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_exe_super ON public.act_ru_execution USING btree (super_exec_);


--
-- Name: act_idx_exec_buskey; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_exec_buskey ON public.act_ru_execution USING btree (business_key_);


--
-- Name: act_idx_exec_ref_id_; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_exec_ref_id_ ON public.act_ru_execution USING btree (reference_id_);


--
-- Name: act_idx_external_job_correlation_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_external_job_correlation_id ON public.act_ru_external_job USING btree (correlation_id_);


--
-- Name: act_idx_external_job_custom_values_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_external_job_custom_values_id ON public.act_ru_external_job USING btree (custom_values_id_);


--
-- Name: act_idx_external_job_exception_stack_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_external_job_exception_stack_id ON public.act_ru_external_job USING btree (exception_stack_id_);


--
-- Name: act_idx_hi_act_inst_end; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_act_inst_end ON public.act_hi_actinst USING btree (end_time_);


--
-- Name: act_idx_hi_act_inst_exec; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_act_inst_exec ON public.act_hi_actinst USING btree (execution_id_, act_id_);


--
-- Name: act_idx_hi_act_inst_procinst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_act_inst_procinst ON public.act_hi_actinst USING btree (proc_inst_id_, act_id_);


--
-- Name: act_idx_hi_act_inst_start; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_act_inst_start ON public.act_hi_actinst USING btree (start_time_);


--
-- Name: act_idx_hi_detail_act_inst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_detail_act_inst ON public.act_hi_detail USING btree (act_inst_id_);


--
-- Name: act_idx_hi_detail_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_detail_name ON public.act_hi_detail USING btree (name_);


--
-- Name: act_idx_hi_detail_proc_inst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_detail_proc_inst ON public.act_hi_detail USING btree (proc_inst_id_);


--
-- Name: act_idx_hi_detail_task_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_detail_task_id ON public.act_hi_detail USING btree (task_id_);


--
-- Name: act_idx_hi_detail_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_detail_time ON public.act_hi_detail USING btree (time_);


--
-- Name: act_idx_hi_ent_lnk_ref_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_ent_lnk_ref_scope ON public.act_hi_entitylink USING btree (ref_scope_id_, ref_scope_type_, link_type_);


--
-- Name: act_idx_hi_ent_lnk_root_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_ent_lnk_root_scope ON public.act_hi_entitylink USING btree (root_scope_id_, root_scope_type_, link_type_);


--
-- Name: act_idx_hi_ent_lnk_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_ent_lnk_scope ON public.act_hi_entitylink USING btree (scope_id_, scope_type_, link_type_);


--
-- Name: act_idx_hi_ent_lnk_scope_def; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_ent_lnk_scope_def ON public.act_hi_entitylink USING btree (scope_definition_id_, scope_type_, link_type_);


--
-- Name: act_idx_hi_ident_lnk_procinst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_ident_lnk_procinst ON public.act_hi_identitylink USING btree (proc_inst_id_);


--
-- Name: act_idx_hi_ident_lnk_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_ident_lnk_scope ON public.act_hi_identitylink USING btree (scope_id_, scope_type_);


--
-- Name: act_idx_hi_ident_lnk_scope_def; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_ident_lnk_scope_def ON public.act_hi_identitylink USING btree (scope_definition_id_, scope_type_);


--
-- Name: act_idx_hi_ident_lnk_sub_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_ident_lnk_sub_scope ON public.act_hi_identitylink USING btree (sub_scope_id_, scope_type_);


--
-- Name: act_idx_hi_ident_lnk_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_ident_lnk_task ON public.act_hi_identitylink USING btree (task_id_);


--
-- Name: act_idx_hi_ident_lnk_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_ident_lnk_user ON public.act_hi_identitylink USING btree (user_id_);


--
-- Name: act_idx_hi_pro_i_buskey; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_pro_i_buskey ON public.act_hi_procinst USING btree (business_key_);


--
-- Name: act_idx_hi_pro_inst_end; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_pro_inst_end ON public.act_hi_procinst USING btree (end_time_);


--
-- Name: act_idx_hi_pro_super_procinst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_pro_super_procinst ON public.act_hi_procinst USING btree (super_process_instance_id_);


--
-- Name: act_idx_hi_procvar_exe; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_procvar_exe ON public.act_hi_varinst USING btree (execution_id_);


--
-- Name: act_idx_hi_procvar_name_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_procvar_name_type ON public.act_hi_varinst USING btree (name_, var_type_);


--
-- Name: act_idx_hi_procvar_proc_inst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_procvar_proc_inst ON public.act_hi_varinst USING btree (proc_inst_id_);


--
-- Name: act_idx_hi_procvar_task_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_procvar_task_id ON public.act_hi_varinst USING btree (task_id_);


--
-- Name: act_idx_hi_task_inst_procinst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_task_inst_procinst ON public.act_hi_taskinst USING btree (proc_inst_id_);


--
-- Name: act_idx_hi_task_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_task_scope ON public.act_hi_taskinst USING btree (scope_id_, scope_type_);


--
-- Name: act_idx_hi_task_scope_def; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_task_scope_def ON public.act_hi_taskinst USING btree (scope_definition_id_, scope_type_);


--
-- Name: act_idx_hi_task_sub_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_task_sub_scope ON public.act_hi_taskinst USING btree (sub_scope_id_, scope_type_);


--
-- Name: act_idx_hi_var_scope_id_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_var_scope_id_type ON public.act_hi_varinst USING btree (scope_id_, scope_type_);


--
-- Name: act_idx_hi_var_sub_id_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_hi_var_sub_id_type ON public.act_hi_varinst USING btree (sub_scope_id_, scope_type_);


--
-- Name: act_idx_ident_lnk_group; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ident_lnk_group ON public.act_ru_identitylink USING btree (group_id_);


--
-- Name: act_idx_ident_lnk_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ident_lnk_scope ON public.act_ru_identitylink USING btree (scope_id_, scope_type_);


--
-- Name: act_idx_ident_lnk_scope_def; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ident_lnk_scope_def ON public.act_ru_identitylink USING btree (scope_definition_id_, scope_type_);


--
-- Name: act_idx_ident_lnk_sub_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ident_lnk_sub_scope ON public.act_ru_identitylink USING btree (sub_scope_id_, scope_type_);


--
-- Name: act_idx_ident_lnk_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ident_lnk_user ON public.act_ru_identitylink USING btree (user_id_);


--
-- Name: act_idx_idl_procinst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_idl_procinst ON public.act_ru_identitylink USING btree (proc_inst_id_);


--
-- Name: act_idx_job_correlation_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_job_correlation_id ON public.act_ru_job USING btree (correlation_id_);


--
-- Name: act_idx_job_custom_values_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_job_custom_values_id ON public.act_ru_job USING btree (custom_values_id_);


--
-- Name: act_idx_job_exception_stack_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_job_exception_stack_id ON public.act_ru_job USING btree (exception_stack_id_);


--
-- Name: act_idx_job_execution_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_job_execution_id ON public.act_ru_job USING btree (execution_id_);


--
-- Name: act_idx_job_proc_def_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_job_proc_def_id ON public.act_ru_job USING btree (proc_def_id_);


--
-- Name: act_idx_job_process_instance_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_job_process_instance_id ON public.act_ru_job USING btree (process_instance_id_);


--
-- Name: act_idx_job_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_job_scope ON public.act_ru_job USING btree (scope_id_, scope_type_);


--
-- Name: act_idx_job_scope_def; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_job_scope_def ON public.act_ru_job USING btree (scope_definition_id_, scope_type_);


--
-- Name: act_idx_job_sub_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_job_sub_scope ON public.act_ru_job USING btree (sub_scope_id_, scope_type_);


--
-- Name: act_idx_memb_group; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_memb_group ON public.act_id_membership USING btree (group_id_);


--
-- Name: act_idx_memb_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_memb_user ON public.act_id_membership USING btree (user_id_);


--
-- Name: act_idx_model_deployment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_model_deployment ON public.act_re_model USING btree (deployment_id_);


--
-- Name: act_idx_model_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_model_source ON public.act_re_model USING btree (editor_source_value_id_);


--
-- Name: act_idx_model_source_extra; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_model_source_extra ON public.act_re_model USING btree (editor_source_extra_value_id_);


--
-- Name: act_idx_priv_group; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_priv_group ON public.act_id_priv_mapping USING btree (group_id_);


--
-- Name: act_idx_priv_mapping; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_priv_mapping ON public.act_id_priv_mapping USING btree (priv_id_);


--
-- Name: act_idx_priv_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_priv_user ON public.act_id_priv_mapping USING btree (user_id_);


--
-- Name: act_idx_procdef_info_json; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_procdef_info_json ON public.act_procdef_info USING btree (info_json_id_);


--
-- Name: act_idx_procdef_info_proc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_procdef_info_proc ON public.act_procdef_info USING btree (proc_def_id_);


--
-- Name: act_idx_ru_acti_end; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ru_acti_end ON public.act_ru_actinst USING btree (end_time_);


--
-- Name: act_idx_ru_acti_exec; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ru_acti_exec ON public.act_ru_actinst USING btree (execution_id_);


--
-- Name: act_idx_ru_acti_exec_act; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ru_acti_exec_act ON public.act_ru_actinst USING btree (execution_id_, act_id_);


--
-- Name: act_idx_ru_acti_proc; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ru_acti_proc ON public.act_ru_actinst USING btree (proc_inst_id_);


--
-- Name: act_idx_ru_acti_proc_act; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ru_acti_proc_act ON public.act_ru_actinst USING btree (proc_inst_id_, act_id_);


--
-- Name: act_idx_ru_acti_start; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ru_acti_start ON public.act_ru_actinst USING btree (start_time_);


--
-- Name: act_idx_ru_acti_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ru_acti_task ON public.act_ru_actinst USING btree (task_id_);


--
-- Name: act_idx_ru_var_scope_id_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ru_var_scope_id_type ON public.act_ru_variable USING btree (scope_id_, scope_type_);


--
-- Name: act_idx_ru_var_sub_id_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_ru_var_sub_id_type ON public.act_ru_variable USING btree (sub_scope_id_, scope_type_);


--
-- Name: act_idx_sjob_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_sjob_scope ON public.act_ru_suspended_job USING btree (scope_id_, scope_type_);


--
-- Name: act_idx_sjob_scope_def; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_sjob_scope_def ON public.act_ru_suspended_job USING btree (scope_definition_id_, scope_type_);


--
-- Name: act_idx_sjob_sub_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_sjob_sub_scope ON public.act_ru_suspended_job USING btree (sub_scope_id_, scope_type_);


--
-- Name: act_idx_suspended_job_correlation_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_suspended_job_correlation_id ON public.act_ru_suspended_job USING btree (correlation_id_);


--
-- Name: act_idx_suspended_job_custom_values_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_suspended_job_custom_values_id ON public.act_ru_suspended_job USING btree (custom_values_id_);


--
-- Name: act_idx_suspended_job_exception_stack_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_suspended_job_exception_stack_id ON public.act_ru_suspended_job USING btree (exception_stack_id_);


--
-- Name: act_idx_suspended_job_execution_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_suspended_job_execution_id ON public.act_ru_suspended_job USING btree (execution_id_);


--
-- Name: act_idx_suspended_job_proc_def_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_suspended_job_proc_def_id ON public.act_ru_suspended_job USING btree (proc_def_id_);


--
-- Name: act_idx_suspended_job_process_instance_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_suspended_job_process_instance_id ON public.act_ru_suspended_job USING btree (process_instance_id_);


--
-- Name: act_idx_task_create; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_task_create ON public.act_ru_task USING btree (create_time_);


--
-- Name: act_idx_task_exec; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_task_exec ON public.act_ru_task USING btree (execution_id_);


--
-- Name: act_idx_task_procdef; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_task_procdef ON public.act_ru_task USING btree (proc_def_id_);


--
-- Name: act_idx_task_procinst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_task_procinst ON public.act_ru_task USING btree (proc_inst_id_);


--
-- Name: act_idx_task_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_task_scope ON public.act_ru_task USING btree (scope_id_, scope_type_);


--
-- Name: act_idx_task_scope_def; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_task_scope_def ON public.act_ru_task USING btree (scope_definition_id_, scope_type_);


--
-- Name: act_idx_task_sub_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_task_sub_scope ON public.act_ru_task USING btree (sub_scope_id_, scope_type_);


--
-- Name: act_idx_timer_job_correlation_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_timer_job_correlation_id ON public.act_ru_timer_job USING btree (correlation_id_);


--
-- Name: act_idx_timer_job_custom_values_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_timer_job_custom_values_id ON public.act_ru_timer_job USING btree (custom_values_id_);


--
-- Name: act_idx_timer_job_duedate; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_timer_job_duedate ON public.act_ru_timer_job USING btree (duedate_);


--
-- Name: act_idx_timer_job_exception_stack_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_timer_job_exception_stack_id ON public.act_ru_timer_job USING btree (exception_stack_id_);


--
-- Name: act_idx_timer_job_execution_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_timer_job_execution_id ON public.act_ru_timer_job USING btree (execution_id_);


--
-- Name: act_idx_timer_job_proc_def_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_timer_job_proc_def_id ON public.act_ru_timer_job USING btree (proc_def_id_);


--
-- Name: act_idx_timer_job_process_instance_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_timer_job_process_instance_id ON public.act_ru_timer_job USING btree (process_instance_id_);


--
-- Name: act_idx_tjob_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_tjob_scope ON public.act_ru_timer_job USING btree (scope_id_, scope_type_);


--
-- Name: act_idx_tjob_scope_def; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_tjob_scope_def ON public.act_ru_timer_job USING btree (scope_definition_id_, scope_type_);


--
-- Name: act_idx_tjob_sub_scope; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_tjob_sub_scope ON public.act_ru_timer_job USING btree (sub_scope_id_, scope_type_);


--
-- Name: act_idx_tskass_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_tskass_task ON public.act_ru_identitylink USING btree (task_id_);


--
-- Name: act_idx_var_bytearray; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_var_bytearray ON public.act_ru_variable USING btree (bytearray_id_);


--
-- Name: act_idx_var_exe; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_var_exe ON public.act_ru_variable USING btree (execution_id_);


--
-- Name: act_idx_var_procinst; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_var_procinst ON public.act_ru_variable USING btree (proc_inst_id_);


--
-- Name: act_idx_variable_task_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX act_idx_variable_task_id ON public.act_ru_variable USING btree (task_id_);


--
-- Name: flw_idx_batch_part; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flw_idx_batch_part ON public.flw_ru_batch_part USING btree (batch_id_);


--
-- Name: flw_idx_event_rsrc_dpl; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flw_idx_event_rsrc_dpl ON public.flw_event_resource USING btree (deployment_id_);


--
-- Name: idx_agent_proposal_semester_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_agent_proposal_semester_status ON public.edu_scheduling_agent_proposal USING btree (semester_code, status, create_time DESC);


--
-- Name: idx_edu_assignment_teacher_term; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_edu_assignment_teacher_term ON public.edu_teacher_teaching_assignment USING btree (teacher_id, academic_term_id);


--
-- Name: idx_edu_candidate_semester_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_edu_candidate_semester_time ON public.edu_schedule_candidate_plan USING btree (semester_code, generated_at DESC);


--
-- Name: idx_edu_class_grade; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_edu_class_grade ON public.edu_administrative_class USING btree (grade_id);


--
-- Name: idx_edu_course_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_edu_course_subject ON public.edu_course_catalog USING btree (subject_id);


--
-- Name: idx_edu_guardian_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_edu_guardian_parent ON public.edu_student_guardian USING btree (parent_id);


--
-- Name: idx_edu_guardian_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_edu_guardian_student ON public.edu_student_guardian USING btree (student_id);


--
-- Name: idx_edu_offering_teacher; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_edu_offering_teacher ON public.edu_course_offering USING btree (semester_code, teacher_id);


--
-- Name: idx_edu_schedule_room; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_edu_schedule_room ON public.edu_schedule_entry USING btree (classroom_id);


--
-- Name: idx_edu_schedule_semester_slot; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_edu_schedule_semester_slot ON public.edu_schedule_entry USING btree (semester_code, day_of_week, period_no);


--
-- Name: idx_edu_schedule_version_semester; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_edu_schedule_version_semester ON public.edu_schedule_plan_version USING btree (semester_code, version_no DESC);


--
-- Name: idx_edu_student_grade; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_edu_student_grade ON public.edu_student_profile USING btree (grade_id);


--
-- Name: idx_edu_user_profile_target; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_edu_user_profile_target ON public.edu_user_profile_binding USING btree (profile_type, profile_id);


--
-- Name: idx_kb_chunk_document; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_chunk_document ON public.kb_document_chunk USING btree (document_id, chunk_index);


--
-- Name: idx_kb_document_base; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_kb_document_base ON public.kb_document USING btree (knowledge_base_id, create_time DESC);


--
-- Name: idx_managed_file_business; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_managed_file_business ON public.t_managed_file USING btree (business_type, business_id, status);


--
-- Name: idx_managed_file_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_managed_file_owner ON public.t_managed_file USING btree (owner_username, status, create_time DESC);


--
-- Name: idx_role_data_scope_resource; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_role_data_scope_resource ON public.t_role_data_scope USING btree (role_id, resource_type, resource_id);


--
-- Name: idx_wf_acl_definition_action; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_acl_definition_action ON public.wf_definition_acl USING btree (definition_id, action);


--
-- Name: idx_wf_acl_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_acl_subject ON public.wf_definition_acl USING btree (subject_type, subject_id);


--
-- Name: idx_wf_candidate_subject; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_candidate_subject ON public.wf_task_candidate USING btree (subject_type, subject_id);


--
-- Name: idx_wf_candidate_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_candidate_task ON public.wf_task_candidate USING btree (task_id);


--
-- Name: idx_wf_delegation_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_delegation_active ON public.wf_delegation USING btree (delegator, enabled, start_at, end_at);


--
-- Name: idx_wf_execution_instance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_execution_instance ON public.wf_execution_log USING btree (instance_id, create_time);


--
-- Name: idx_wf_execution_node; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_execution_node ON public.wf_execution_log USING btree (node_id, create_time);


--
-- Name: idx_wf_incident_instance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_incident_instance ON public.wf_incident USING btree (instance_id);


--
-- Name: idx_wf_incident_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_incident_status ON public.wf_incident USING btree (status, next_retry_at);


--
-- Name: idx_wf_instance_engine_instance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_instance_engine_instance ON public.wf_instance USING btree (engine_instance_id);


--
-- Name: idx_wf_notification_recipient; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_notification_recipient ON public.wf_notification USING btree (recipient, read_at);


--
-- Name: idx_wf_notification_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_notification_task ON public.wf_notification USING btree (task_id);


--
-- Name: idx_wf_outbox_dispatch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_outbox_dispatch ON public.wf_outbox USING btree (status, next_attempt_at);


--
-- Name: idx_wf_participant_instance; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_participant_instance ON public.wf_instance_participant USING btree (instance_id);


--
-- Name: idx_wf_participant_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_participant_user ON public.wf_instance_participant USING btree (username, participant_type);


--
-- Name: idx_wf_task_engine_task; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_wf_task_engine_task ON public.wf_task USING btree (engine_task_id);


--
-- Name: act_ru_identitylink act_fk_athrz_procedef; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_identitylink
    ADD CONSTRAINT act_fk_athrz_procedef FOREIGN KEY (proc_def_id_) REFERENCES public.act_re_procdef(id_);


--
-- Name: act_ge_bytearray act_fk_bytearr_depl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ge_bytearray
    ADD CONSTRAINT act_fk_bytearr_depl FOREIGN KEY (deployment_id_) REFERENCES public.act_re_deployment(id_);


--
-- Name: act_ru_deadletter_job act_fk_deadletter_job_custom_values; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_deadletter_job
    ADD CONSTRAINT act_fk_deadletter_job_custom_values FOREIGN KEY (custom_values_id_) REFERENCES public.act_ge_bytearray(id_);


--
-- Name: act_ru_deadletter_job act_fk_deadletter_job_exception; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_deadletter_job
    ADD CONSTRAINT act_fk_deadletter_job_exception FOREIGN KEY (exception_stack_id_) REFERENCES public.act_ge_bytearray(id_);


--
-- Name: act_ru_deadletter_job act_fk_deadletter_job_execution; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_deadletter_job
    ADD CONSTRAINT act_fk_deadletter_job_execution FOREIGN KEY (execution_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_ru_deadletter_job act_fk_deadletter_job_proc_def; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_deadletter_job
    ADD CONSTRAINT act_fk_deadletter_job_proc_def FOREIGN KEY (proc_def_id_) REFERENCES public.act_re_procdef(id_);


--
-- Name: act_ru_deadletter_job act_fk_deadletter_job_process_instance; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_deadletter_job
    ADD CONSTRAINT act_fk_deadletter_job_process_instance FOREIGN KEY (process_instance_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_ru_event_subscr act_fk_event_exec; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_event_subscr
    ADD CONSTRAINT act_fk_event_exec FOREIGN KEY (execution_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_ru_execution act_fk_exe_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_execution
    ADD CONSTRAINT act_fk_exe_parent FOREIGN KEY (parent_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_ru_execution act_fk_exe_procdef; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_execution
    ADD CONSTRAINT act_fk_exe_procdef FOREIGN KEY (proc_def_id_) REFERENCES public.act_re_procdef(id_);


--
-- Name: act_ru_execution act_fk_exe_procinst; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_execution
    ADD CONSTRAINT act_fk_exe_procinst FOREIGN KEY (proc_inst_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_ru_execution act_fk_exe_super; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_execution
    ADD CONSTRAINT act_fk_exe_super FOREIGN KEY (super_exec_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_ru_external_job act_fk_external_job_custom_values; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_external_job
    ADD CONSTRAINT act_fk_external_job_custom_values FOREIGN KEY (custom_values_id_) REFERENCES public.act_ge_bytearray(id_);


--
-- Name: act_ru_external_job act_fk_external_job_exception; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_external_job
    ADD CONSTRAINT act_fk_external_job_exception FOREIGN KEY (exception_stack_id_) REFERENCES public.act_ge_bytearray(id_);


--
-- Name: act_ru_identitylink act_fk_idl_procinst; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_identitylink
    ADD CONSTRAINT act_fk_idl_procinst FOREIGN KEY (proc_inst_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_procdef_info act_fk_info_json_ba; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_procdef_info
    ADD CONSTRAINT act_fk_info_json_ba FOREIGN KEY (info_json_id_) REFERENCES public.act_ge_bytearray(id_);


--
-- Name: act_procdef_info act_fk_info_procdef; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_procdef_info
    ADD CONSTRAINT act_fk_info_procdef FOREIGN KEY (proc_def_id_) REFERENCES public.act_re_procdef(id_);


--
-- Name: act_ru_job act_fk_job_custom_values; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_job
    ADD CONSTRAINT act_fk_job_custom_values FOREIGN KEY (custom_values_id_) REFERENCES public.act_ge_bytearray(id_);


--
-- Name: act_ru_job act_fk_job_exception; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_job
    ADD CONSTRAINT act_fk_job_exception FOREIGN KEY (exception_stack_id_) REFERENCES public.act_ge_bytearray(id_);


--
-- Name: act_ru_job act_fk_job_execution; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_job
    ADD CONSTRAINT act_fk_job_execution FOREIGN KEY (execution_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_ru_job act_fk_job_proc_def; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_job
    ADD CONSTRAINT act_fk_job_proc_def FOREIGN KEY (proc_def_id_) REFERENCES public.act_re_procdef(id_);


--
-- Name: act_ru_job act_fk_job_process_instance; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_job
    ADD CONSTRAINT act_fk_job_process_instance FOREIGN KEY (process_instance_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_id_membership act_fk_memb_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_id_membership
    ADD CONSTRAINT act_fk_memb_group FOREIGN KEY (group_id_) REFERENCES public.act_id_group(id_);


--
-- Name: act_id_membership act_fk_memb_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_id_membership
    ADD CONSTRAINT act_fk_memb_user FOREIGN KEY (user_id_) REFERENCES public.act_id_user(id_);


--
-- Name: act_re_model act_fk_model_deployment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_re_model
    ADD CONSTRAINT act_fk_model_deployment FOREIGN KEY (deployment_id_) REFERENCES public.act_re_deployment(id_);


--
-- Name: act_re_model act_fk_model_source; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_re_model
    ADD CONSTRAINT act_fk_model_source FOREIGN KEY (editor_source_value_id_) REFERENCES public.act_ge_bytearray(id_);


--
-- Name: act_re_model act_fk_model_source_extra; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_re_model
    ADD CONSTRAINT act_fk_model_source_extra FOREIGN KEY (editor_source_extra_value_id_) REFERENCES public.act_ge_bytearray(id_);


--
-- Name: act_id_priv_mapping act_fk_priv_mapping; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_id_priv_mapping
    ADD CONSTRAINT act_fk_priv_mapping FOREIGN KEY (priv_id_) REFERENCES public.act_id_priv(id_);


--
-- Name: act_ru_suspended_job act_fk_suspended_job_custom_values; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_suspended_job
    ADD CONSTRAINT act_fk_suspended_job_custom_values FOREIGN KEY (custom_values_id_) REFERENCES public.act_ge_bytearray(id_);


--
-- Name: act_ru_suspended_job act_fk_suspended_job_exception; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_suspended_job
    ADD CONSTRAINT act_fk_suspended_job_exception FOREIGN KEY (exception_stack_id_) REFERENCES public.act_ge_bytearray(id_);


--
-- Name: act_ru_suspended_job act_fk_suspended_job_execution; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_suspended_job
    ADD CONSTRAINT act_fk_suspended_job_execution FOREIGN KEY (execution_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_ru_suspended_job act_fk_suspended_job_proc_def; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_suspended_job
    ADD CONSTRAINT act_fk_suspended_job_proc_def FOREIGN KEY (proc_def_id_) REFERENCES public.act_re_procdef(id_);


--
-- Name: act_ru_suspended_job act_fk_suspended_job_process_instance; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_suspended_job
    ADD CONSTRAINT act_fk_suspended_job_process_instance FOREIGN KEY (process_instance_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_ru_task act_fk_task_exe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_task
    ADD CONSTRAINT act_fk_task_exe FOREIGN KEY (execution_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_ru_task act_fk_task_procdef; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_task
    ADD CONSTRAINT act_fk_task_procdef FOREIGN KEY (proc_def_id_) REFERENCES public.act_re_procdef(id_);


--
-- Name: act_ru_task act_fk_task_procinst; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_task
    ADD CONSTRAINT act_fk_task_procinst FOREIGN KEY (proc_inst_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_ru_timer_job act_fk_timer_job_custom_values; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_timer_job
    ADD CONSTRAINT act_fk_timer_job_custom_values FOREIGN KEY (custom_values_id_) REFERENCES public.act_ge_bytearray(id_);


--
-- Name: act_ru_timer_job act_fk_timer_job_exception; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_timer_job
    ADD CONSTRAINT act_fk_timer_job_exception FOREIGN KEY (exception_stack_id_) REFERENCES public.act_ge_bytearray(id_);


--
-- Name: act_ru_timer_job act_fk_timer_job_execution; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_timer_job
    ADD CONSTRAINT act_fk_timer_job_execution FOREIGN KEY (execution_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_ru_timer_job act_fk_timer_job_proc_def; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_timer_job
    ADD CONSTRAINT act_fk_timer_job_proc_def FOREIGN KEY (proc_def_id_) REFERENCES public.act_re_procdef(id_);


--
-- Name: act_ru_timer_job act_fk_timer_job_process_instance; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_timer_job
    ADD CONSTRAINT act_fk_timer_job_process_instance FOREIGN KEY (process_instance_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_ru_identitylink act_fk_tskass_task; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_identitylink
    ADD CONSTRAINT act_fk_tskass_task FOREIGN KEY (task_id_) REFERENCES public.act_ru_task(id_);


--
-- Name: act_ru_variable act_fk_var_bytearray; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_variable
    ADD CONSTRAINT act_fk_var_bytearray FOREIGN KEY (bytearray_id_) REFERENCES public.act_ge_bytearray(id_);


--
-- Name: act_ru_variable act_fk_var_exe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_variable
    ADD CONSTRAINT act_fk_var_exe FOREIGN KEY (execution_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: act_ru_variable act_fk_var_procinst; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.act_ru_variable
    ADD CONSTRAINT act_fk_var_procinst FOREIGN KEY (proc_inst_id_) REFERENCES public.act_ru_execution(id_);


--
-- Name: edu_schedule_entry edu_schedule_entry_classroom_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_schedule_entry
    ADD CONSTRAINT edu_schedule_entry_classroom_id_fkey FOREIGN KEY (classroom_id) REFERENCES public.edu_classroom(id);


--
-- Name: edu_schedule_entry edu_schedule_entry_offering_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_schedule_entry
    ADD CONSTRAINT edu_schedule_entry_offering_id_fkey FOREIGN KEY (offering_id) REFERENCES public.edu_course_offering(id);


--
-- Name: edu_scheduling_agent_proposal edu_scheduling_agent_proposal_teacher_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_scheduling_agent_proposal
    ADD CONSTRAINT edu_scheduling_agent_proposal_teacher_id_fkey FOREIGN KEY (teacher_id) REFERENCES public.edu_teacher_profile(id);


--
-- Name: edu_student_guardian edu_student_guardian_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_student_guardian
    ADD CONSTRAINT edu_student_guardian_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.edu_parent_profile(id);


--
-- Name: edu_student_guardian edu_student_guardian_student_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_student_guardian
    ADD CONSTRAINT edu_student_guardian_student_id_fkey FOREIGN KEY (student_id) REFERENCES public.edu_student_profile(id);


--
-- Name: edu_teacher_teaching_assignment edu_teacher_teaching_assignment_academic_term_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_teacher_teaching_assignment
    ADD CONSTRAINT edu_teacher_teaching_assignment_academic_term_id_fkey FOREIGN KEY (academic_term_id) REFERENCES public.edu_academic_term(id);


--
-- Name: edu_teacher_teaching_assignment edu_teacher_teaching_assignment_administrative_class_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_teacher_teaching_assignment
    ADD CONSTRAINT edu_teacher_teaching_assignment_administrative_class_id_fkey FOREIGN KEY (administrative_class_id) REFERENCES public.edu_administrative_class(id);


--
-- Name: edu_teacher_teaching_assignment edu_teacher_teaching_assignment_grade_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_teacher_teaching_assignment
    ADD CONSTRAINT edu_teacher_teaching_assignment_grade_id_fkey FOREIGN KEY (grade_id) REFERENCES public.edu_grade(id);


--
-- Name: edu_teacher_teaching_assignment edu_teacher_teaching_assignment_subject_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_teacher_teaching_assignment
    ADD CONSTRAINT edu_teacher_teaching_assignment_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES public.edu_subject(id);


--
-- Name: edu_teacher_teaching_assignment edu_teacher_teaching_assignment_teacher_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.edu_teacher_teaching_assignment
    ADD CONSTRAINT edu_teacher_teaching_assignment_teacher_id_fkey FOREIGN KEY (teacher_id) REFERENCES public.edu_teacher_profile(id);


--
-- Name: t_organization fk27ri7ofj6rqv2609cpfduypt8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_organization
    ADD CONSTRAINT fk27ri7ofj6rqv2609cpfduypt8 FOREIGN KEY (parent_org_id) REFERENCES public.t_organization(id);


--
-- Name: t_user_role fk3sqwcssw7sadeg7q2c0bxr5eh; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user_role
    ADD CONSTRAINT fk3sqwcssw7sadeg7q2c0bxr5eh FOREIGN KEY (user_id) REFERENCES public.t_admin_user(id);


--
-- Name: d_resource_capability fk56293ne5ufekbjxp6k0t28y9s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_resource_capability
    ADD CONSTRAINT fk56293ne5ufekbjxp6k0t28y9s FOREIGN KEY (resource_id) REFERENCES public.d_resource(id);


--
-- Name: d_schedule_assignment fk6t7fivmr4v78i7qlhbvfkge33; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_schedule_assignment
    ADD CONSTRAINT fk6t7fivmr4v78i7qlhbvfkge33 FOREIGN KEY (schedule_result_id) REFERENCES public.d_schedule_result(id);


--
-- Name: t_user_role fka9c8iiy6ut0gnx491fqx4pxam; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_user_role
    ADD CONSTRAINT fka9c8iiy6ut0gnx491fqx4pxam FOREIGN KEY (role_id) REFERENCES public.t_role(id);


--
-- Name: t_organization_unit fkdss4opsu4ev2isipkcvh16k1h; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_organization_unit
    ADD CONSTRAINT fkdss4opsu4ev2isipkcvh16k1h FOREIGN KEY (parent_organization_unit_id) REFERENCES public.t_organization_unit(id);


--
-- Name: d_resource_capability fkf04mt0h9i2nwc07h3jxoxfs4o; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_resource_capability
    ADD CONSTRAINT fkf04mt0h9i2nwc07h3jxoxfs4o FOREIGN KEY (capability_id) REFERENCES public.d_capability(id);


--
-- Name: t_role_menu fkhayg4ib6v7h1wyeyxhq6xlddq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_role_menu
    ADD CONSTRAINT fkhayg4ib6v7h1wyeyxhq6xlddq FOREIGN KEY (menu_id) REFERENCES public.t_menu(id);


--
-- Name: d_task_capability_requirement fki5av7uwjerpa31fnw2xu0rmh4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_task_capability_requirement
    ADD CONSTRAINT fki5av7uwjerpa31fnw2xu0rmh4 FOREIGN KEY (task_id) REFERENCES public.d_task(id);


--
-- Name: d_task_capability_requirement fkm7cof39r41ujnr6hfrjmo3eqe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_task_capability_requirement
    ADD CONSTRAINT fkm7cof39r41ujnr6hfrjmo3eqe FOREIGN KEY (capability_id) REFERENCES public.d_capability(id);


--
-- Name: d_schedule_assignment fkmbldok5umch4gsqhbdpapdh4t; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_schedule_assignment
    ADD CONSTRAINT fkmbldok5umch4gsqhbdpapdh4t FOREIGN KEY (resource_id) REFERENCES public.d_resource(id);


--
-- Name: t_role_menu fksonb0rbt2u99hbrqqvv3r0wse; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.t_role_menu
    ADD CONSTRAINT fksonb0rbt2u99hbrqqvv3r0wse FOREIGN KEY (role_id) REFERENCES public.t_role(id);


--
-- Name: d_schedule_assignment fkt7kt2uhrkcdw86gybdv9f6jtd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.d_schedule_assignment
    ADD CONSTRAINT fkt7kt2uhrkcdw86gybdv9f6jtd FOREIGN KEY (task_id) REFERENCES public.d_task(id);


--
-- Name: flw_ru_batch_part flw_fk_batch_part_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flw_ru_batch_part
    ADD CONSTRAINT flw_fk_batch_part_parent FOREIGN KEY (batch_id_) REFERENCES public.flw_ru_batch(id_);


--
-- Name: flw_event_resource flw_fk_event_rsrc_dpl; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flw_event_resource
    ADD CONSTRAINT flw_fk_event_rsrc_dpl FOREIGN KEY (deployment_id_) REFERENCES public.flw_event_deployment(id_);


--
-- Name: kb_document_chunk kb_document_chunk_document_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document_chunk
    ADD CONSTRAINT kb_document_chunk_document_id_fkey FOREIGN KEY (document_id) REFERENCES public.kb_document(id) ON DELETE CASCADE;


--
-- Name: kb_document kb_document_knowledge_base_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.kb_document
    ADD CONSTRAINT kb_document_knowledge_base_id_fkey FOREIGN KEY (knowledge_base_id) REFERENCES public.kb_knowledge_base(id);


--
-- PostgreSQL database dump complete
--

-- Flowable 的表结构由本基线直接创建，因此必须同时登记对应的引擎版本。
-- 如果缺少这些记录，Flowable 会把已经是 8.0.0 的表误判为旧版本并重复执行升级 DDL。
INSERT INTO public.act_ge_property (name_, value_, rev_)
VALUES
    ('cfg.execution-related-entities-count', 'true', 1),
    ('cfg.task-related-entities-count', 'true', 1),
    ('common.schema.version', '8.0.0.0', 1),
    ('eventregistry.schema.version', '8.0.0.0', 1),
    ('next.dbid', '1', 1),
    ('schema.history', 'create(8.0.0.0)', 1),
    ('schema.version', '8.0.0.0', 1);

INSERT INTO public.act_id_property (name_, value_, rev_)
VALUES
    ('schema.version', '8.0.0.0', 1);

