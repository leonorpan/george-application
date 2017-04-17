# Contributing to George

This page says something about:

- Rights
- Governance
- Getting started
- "How we do things here"


## Rights

George is distributed under Eclipse Public License 1.0 (EPL1).  
Simply put, it means that you may use and distribute it freely, but any changes you make you must publish.

Terje Dahl holds the copyright to George (this project).
Before accepting any contributions, we ask that you transfer copyright to the project. 
The reasons for this are:

1. "Having a single legal entity own the copyright on the entire code base can be useful if the terms of the open source license ever need to be enforced in court. If no single entity has the right to do it, all the contributors might have to cooperate, but some might not have time or even be reachable when the issue arises." (Producing Open Source Software, by Karl Fogel, 2005)
2. Should the project ever become the target of litigation, then you as contributors are insulated from such. 

Keep in mind that the nature of EPL1 is such that you can at any time take the current code base with you and start your own project, thereby ensuring that all contributed work up to current version remains open and free.

**To transfer copyright**, simply post the following as a publicly visible comment on the discussion thread [Copyright Assignment and Ownership](https://groups.google.com/forum/#!topic/george-dev/JYrMKMDmaIs):

> I hereby assign copyright in this code and resources to the project "George", to be licensed under the same terms as the rest of the code.  
> \<your--name--or--contributor-handle--here\>

_Note: If you change your contributor handle or name, please post again with the new handle or name._


## Governance

Terje has a very clear vision for where he wants to guide this project, and also many concrete ideas on design and implementation.  As such this project is at present very much run as a "benevolent dictatorship".

But he is always open to input and discussions on all levels, and grateful for contributions large and small.  So please, don't hesitate to submit queries and ideas, and to open up discussions.

There is a [Google Group: "George Development"](https://groups.google.com/forum/#!forum/george-dev), intended for discussions ranging from technical to design to pedagogy.

You may submit bugs and proposals to the [Bitbucket issue tracker](https://bitbucket.org/andante-george/george-application/issues).

Or you may reach out to Terje directly at [terje@andante.no](mailto:terje@andante.no).


## Getting started

Quick-guide:

1. Create an account on Bitbucket.
2. "fork" the project to your account.
3. "clone" your fork to your computer.
4. Make the changes you want and "commit" and "push".
5. Create a pull request.

A couple of tips:  

Update your Mercurial config file `.hg/hgrc` to include the original project as "upstream".  Then, before commit-ing, you may want to do a `hg pull upstream` to avoid later merge conflicts.

_[Developers, please expand this section further based on shared experiences.]_


## "How we do tings here"

We would prefer that there is an "issue" underlying every pull-request.  This allows for better information and possible discussions around your change.  If you have a change you would like to contribute, but there isn't an issue for it, then create one, and then give it a couple of days for others to comment.

For smaller "one off" changes, do the commit on the `default` branch.  For larger endeavors which might require multiple commits, from more than one developer, and require extensive testing, do a dedicated feature branch. Get clarification on this via the relevant issue on the above mentioned issue tracker. 

If you feel uncertain about something, or would like some input or help on anything code or design related, then do ping Terje, and he will be more than happy to look at your changes in your fork. This may be a useful thing to do before you do a pull-request. That way it is more likely that your pull-request will be accepted once it is made.
