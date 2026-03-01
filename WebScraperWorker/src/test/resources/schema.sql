--
-- PostgreSQL database dump
--

\restrict FJY9IjCOCbLSnHFap92OF7egS3lPSEKnf5DuexYkpBeKbGGmFngGSHs6cj5VeOV

-- Dumped from database version 16.11 (Debian 16.11-1.pgdg13+1)
-- Dumped by pg_dump version 16.11 (Debian 16.11-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.jobs (
    id uuid NOT NULL,
    status character varying(9) DEFAULT 'PENDING'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    completed_at timestamp with time zone,
    claimed_by character varying,
    claimed_at timestamp with time zone,
    heartbeat_at timestamp with time zone,
    seed_urls jsonb NOT NULL,
    max_depth integer NOT NULL,
    pages_fetched integer,
    pages_queued integer,
    attempt_count integer,
    last_error text
);


--
-- Name: pages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.pages (
    id uuid NOT NULL,
    job_id uuid NOT NULL,
    url text NOT NULL,
    depth integer NOT NULL,
    status character varying(9) DEFAULT 'PENDING'::character varying NOT NULL,
    discovered_at timestamp with time zone DEFAULT now(),
    created_at timestamp with time zone DEFAULT now(),
    error text
);


--
-- Name: jobs jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.jobs
    ADD CONSTRAINT jobs_pkey PRIMARY KEY (id);


--
-- Name: pages pages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pages
    ADD CONSTRAINT pages_pkey PRIMARY KEY (id);


--
-- Name: pages uq_job_url; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pages
    ADD CONSTRAINT uq_job_url UNIQUE (job_id, url);


--
-- Name: ix_pages_job_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_pages_job_id ON public.pages USING btree (job_id);


--
-- Name: pages pages_job_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.pages
    ADD CONSTRAINT pages_job_id_fkey FOREIGN KEY (job_id) REFERENCES public.jobs(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict FJY9IjCOCbLSnHFap92OF7egS3lPSEKnf5DuexYkpBeKbGGmFngGSHs6cj5VeOV

